package com.jogodavelha.game.logic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Componente responsável pelo gerenciamento do estado da partida em Redis.
 * Único ponto de acesso ao Redis para estado de partida, com controle de concorrência.
 */
@Slf4j
@Component
public class GameStateManager {

    private static final String KEY_PREFIX = "game:state:";
    private static final int DEFAULT_MAX_RETRIES = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final long stateTtlMillis;
    private final int maxRetries;
    private final RedisScript<Boolean> compareAndSetScript;

    public GameStateManager(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper,
                            @Value("${game.state.ttl:1800000}") long stateTtlMillis,
                            @Value("${game.state.update.max-retries:5}") int maxRetries) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.stateTtlMillis = stateTtlMillis;
        this.maxRetries = maxRetries;
        this.compareAndSetScript = createCompareAndSetScript();
    }

    private RedisScript<Boolean> createCompareAndSetScript() {
        String luaScript = """
            local key = KEYS[1]
            local expectedVersion = tonumber(ARGV[1])
            local newValue = ARGV[2]
            local ttl = tonumber(ARGV[3])
            
            local currentValue = redis.call('GET', key)
            if currentValue == false then
                return 0
            end
            
            local currentData = cjson.decode(currentValue)
            local currentVersion = tonumber(currentData.version)
            
            if currentVersion ~= expectedVersion then
                return 0
            end
            
            redis.call('SET', key, newValue)
            redis.call('EXPIRE', key, ttl)
            return 1
            """;
        
        return new DefaultRedisScript<>(luaScript, Boolean.class);
    }

    /**
     * Cria o estado inicial de uma partida.
     *
     * @param gameId ID da partida
     * @param firstPlayerId ID do jogador que começa
     * @param turnDurationMillis duração do turno em milissegundos
     * @throws IllegalStateException se já existir estado para este gameId
     */
    public void create(UUID gameId, UUID firstPlayerId, long turnDurationMillis) {
        String key = buildKey(gameId);
        
        Boolean alreadyExists = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(alreadyExists)) {
            throw new IllegalStateException("Estado da partida já existe: " + gameId);
        }
        
        GameState gameState = GameState.createInitial(gameId, firstPlayerId, turnDurationMillis);
        String json = serialize(gameState);
        
        redisTemplate.opsForValue().set(key, json, stateTtlMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.debug("Estado inicial criado para partida {}", gameId);
    }

    /**
     * Busca o estado de uma partida.
     *
     * @param gameId ID da partida
     * @return Optional com o estado, vazio se não existir
     */
    public Optional<GameState> find(UUID gameId) {
        String key = buildKey(gameId);
        String json = (String) redisTemplate.opsForValue().get(key);
        
        if (json == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(deserialize(json));
        } catch (JsonProcessingException e) {
            log.error("Erro ao deserializar estado da partida {}", gameId, e);
            return Optional.empty();
        }
    }

    /**
     * Atualiza o estado de uma partida de forma atômica com controle de versão.
     *
     * @param gameId ID da partida
     * @param mutator função que aplica modificações ao estado
     * @throws GameStateNotFoundException se o estado não existir
     * @throws ConcurrentStateModificationException se houver conflito de concorrência após todas as tentativas
     */
    public void update(UUID gameId, Function<GameState, GameState> mutator) {
        String key = buildKey(gameId);
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            Optional<GameState> currentOpt = find(gameId);
            
            if (currentOpt.isEmpty()) {
                throw new GameStateNotFoundException(gameId);
            }
            
            GameState current = currentOpt.get();
            long expectedVersion = current.getVersion();
            
            GameState modified = mutator.apply(current);
            modified.setVersion(expectedVersion + 1);
            
            String json = serialize(modified);
            long ttlSeconds = stateTtlMillis / 1000;
            
            Boolean success = redisTemplate.execute(
                compareAndSetScript,
                Collections.singletonList(key),
                String.valueOf(expectedVersion),
                json,
                String.valueOf(ttlSeconds)
            );
            
            if (Boolean.TRUE.equals(success)) {
                log.debug("Estado atualizado com sucesso para partida {} (versão {})", gameId, modified.getVersion());
                return;
            }
            
            log.debug("Conflito de versão ao atualizar partida {} (tentativa {}/{})", gameId, attempt + 1, maxRetries);
        }
        
        throw new ConcurrentStateModificationException(gameId, maxRetries);
    }

    /**
     * Remove o estado de uma partida.
     *
     * @param gameId ID da partida
     */
    public void delete(UUID gameId) {
        String key = buildKey(gameId);
        redisTemplate.delete(key);
        log.debug("Estado removido para partida {}", gameId);
    }

    private String buildKey(UUID gameId) {
        return KEY_PREFIX + gameId;
    }

    private String serialize(GameState gameState) {
        try {
            return objectMapper.writeValueAsString(gameState);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao serializar estado da partida", e);
        }
    }

    private GameState deserialize(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, GameState.class);
    }
}