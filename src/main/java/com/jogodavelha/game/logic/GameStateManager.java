package com.jogodavelha.game.logic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.telemetry.ActionProfiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
    private final SecureRandom random;
    /**
     * Último JSON commitado por este nó, indexado pelo gameId. Evita o GET anterior ao CAS
     * no caminho feliz: se outra instância gravar, o CAS falha e a leitura volta ao Redis.
     * O mutator precisa ser puro (contrato já existente) porque pode rodar sobre estado
     * eventualmente defasado; nesse caso uma exceção do mutator força releitura.
     * Cada entrada expira junto com o TTL do Redis; o mapa é limitado e entradas
     * expiradas são removidas na leitura e na poda disparada ao ultrapassar o limite.
     */
    private final ConcurrentHashMap<UUID, CacheEntry> committedStates = new ConcurrentHashMap<>();
    private static final int MAX_CACHED_STATES = 4096;

    private record CacheEntry(String json, long version, long expiresAtEpochMillis) { }

    @Autowired
    public GameStateManager(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper,
                            @Value("${game.state.ttl:1800000}") long stateTtlMillis,
                            @Value("${game.state.update.max-retries:5}") int maxRetries) {
        this(redisTemplate, objectMapper, stateTtlMillis, maxRetries, new SecureRandom());
    }

    public GameStateManager(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper,
                            long stateTtlMillis, int maxRetries, SecureRandom random) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.stateTtlMillis = stateTtlMillis;
        this.maxRetries = maxRetries;
        this.compareAndSetScript = createCompareAndSetScript();
        this.random = Objects.requireNonNull(random);
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
        saveInitial(GameState.createInitial(gameId, firstPlayerId, turnDurationMillis));
    }

    public void create(UUID gameId, UUID player1Id, UUID player2Id, long turnDurationMillis) {
        saveInitial(GameState.createInitial(gameId, player1Id, player2Id, turnDurationMillis, random));
    }

    public void create(UUID gameId, String roomCode, UUID player1Id, UUID player2Id, long turnDurationMillis) {
        saveInitial(GameState.createInitial(gameId, roomCode, player1Id, player2Id, turnDurationMillis, random));
    }

    private void saveInitial(GameState gameState) {
        UUID gameId = gameState.getGameId();
        String key = buildKey(gameId);
        String json = serialize(gameState);

        Boolean success = redisTemplate.opsForValue().setIfAbsent(
            key,
            json,
            stateTtlMillis,
            TimeUnit.MILLISECONDS
        );

        if (!Boolean.TRUE.equals(success)) {
            throw new IllegalStateException("Estado da partida já existe: " + gameId);
        }

        remember(gameId, json, gameState.getVersion());
        log.debug("Estado inicial criado para partida {}", gameId);
    }

    /** Entrada válida apenas se ainda não expirou; a expirada é removida na hora. */
    private CacheEntry liveEntry(UUID gameId) {
        CacheEntry entry = committedStates.get(gameId);
        if (entry != null && entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            committedStates.remove(gameId, entry);
            return null;
        }
        return entry;
    }

    /**
     * Remove a entrada somente se ela não for mais nova que {@code version}: uma operação
     * que falhou no CAS sobre um estado antigo não pode derrubar o commit de outra thread.
     */
    private void evictIfNotNewer(UUID gameId, long version) {
        committedStates.computeIfPresent(gameId,
                (id, entry) -> entry.version() <= version ? null : entry);
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        committedStates.values().removeIf(entry -> entry.expiresAtEpochMillis() <= now);
    }

    /**
     * Busca o estado de uma partida.
     *
     * @param gameId ID da partida
     * @return Optional com o estado, vazio se não existir
     */
    public Optional<GameState> find(UUID gameId) {
        String key = buildKey(gameId);
        long readStart = System.nanoTime();
        String json = (String) redisTemplate.opsForValue().get(key);
        ActionProfiler.add("redis.get", readStart);
        
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
     * Em conflito, o mutator é reexecutado sobre o estado relido. Ele deve ser puro:
     * sem broadcast, sorteio ou persistência. Calcule horários e sorteios antes da chamada.
     *
     * @param gameId ID da partida
     * @param mutator função que aplica modificações ao estado
     * @return o estado efetivamente commitado no Redis
     * @throws GameStateNotFoundException se o estado não existir
     * @throws ConcurrentStateModificationException se houver conflito de concorrência após todas as tentativas
     */
    public GameState update(UUID gameId, Function<GameState, GameState> mutator) {
        return updateWithResult(gameId, current -> {
            GameState modified = mutator.apply(current);
            return new Update<>(modified, modified);
        });
    }

    /** Retorna somente o resultado da tentativa que foi gravada pelo CAS. */
    public <T> T updateWithResult(UUID gameId, Function<GameState, Update<T>> mutator) {
        String key = buildKey(gameId);
        
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            GameState current;
            boolean fromCache = false;
            CacheEntry cached = liveEntry(gameId);
            if (cached != null) {
                try {
                    current = deserialize(cached.json());
                    fromCache = true;
                } catch (JsonProcessingException e) {
                    committedStates.remove(gameId, cached);
                    continue;
                }
            } else {
                Optional<GameState> currentOpt = find(gameId);
                if (currentOpt.isEmpty()) {
                    committedStates.remove(gameId);
                    throw new GameStateNotFoundException(gameId);
                }
                current = currentOpt.get();
            }
            
            long expectedVersion = current.getVersion();
            
            Update<T> update;
            try {
                update = mutator.apply(current);
            } catch (RuntimeException failure) {
                // Um mutator que rejeita o estado em cache pode estar vendo dados defasados:
                // descarta o cache (se ninguém gravou algo mais novo) e tenta de novo com
                // a leitura fresca do Redis.
                if (fromCache) {
                    evictIfNotNewer(gameId, current.getVersion());
                    continue;
                }
                throw failure;
            }
            GameState modified = update.state();
            modified.setVersion(expectedVersion + 1);
            
            String json = serialize(modified);
            long ttlSeconds = stateTtlMillis / 1000;
            
            long casStart = System.nanoTime();
            Boolean success = redisTemplate.execute(
                compareAndSetScript,
                Collections.singletonList(key),
                String.valueOf(expectedVersion),
                json,
                String.valueOf(ttlSeconds)
            );
            ActionProfiler.add("redis.cas", casStart);
            
            if (Boolean.TRUE.equals(success)) {
                remember(gameId, json, modified.getVersion());
                log.debug("Estado atualizado com sucesso para partida {} (versão {})", gameId, modified.getVersion());
                return update.result();
            }
            
            evictIfNotNewer(gameId, expectedVersion);
            log.debug("Conflito de versão ao atualizar partida {} (tentativa {}/{})", gameId, attempt + 1, maxRetries);
        }
        
        throw new ConcurrentStateModificationException(gameId, maxRetries);
    }

    private void remember(UUID gameId, String json, long version) {
        committedStates.merge(gameId,
                new CacheEntry(json, version, System.currentTimeMillis() + stateTtlMillis),
                (existing, incoming) -> incoming.version() >= existing.version() ? incoming : existing);
        if (committedStates.size() > MAX_CACHED_STATES) {
            evictExpired();
            // Ainda acima do limite: descarta entradas arbitrárias; são só uma otimização
            // e a próxima leitura volta ao Redis.
            var iterator = committedStates.values().iterator();
            while (committedStates.size() > MAX_CACHED_STATES && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    public record Update<T>(GameState state, T result) { }

    public List<UUID> findAllGameIds() {
        List<UUID> ids = new ArrayList<>();
        try (var keys = redisTemplate.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build())) {
            while (keys.hasNext()) ids.add(UUID.fromString(keys.next().substring(KEY_PREFIX.length())));
        }
        // A varredura é a fonte autoritativa: entradas locais sem chave no Redis são lixo.
        java.util.Set<UUID> live = java.util.Set.copyOf(ids);
        committedStates.keySet().removeIf(id -> !live.contains(id));
        return ids;
    }

    /**
     * Remove o estado de uma partida.
     *
     * @param gameId ID da partida
     */
    public void delete(UUID gameId) {
        String key = buildKey(gameId);
        committedStates.remove(gameId);
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
