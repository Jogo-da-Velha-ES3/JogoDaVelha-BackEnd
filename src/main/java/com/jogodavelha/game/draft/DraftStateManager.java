package com.jogodavelha.game.draft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.logic.ConcurrentStateModificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

@Component
public class DraftStateManager {
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper mapper;
    private final long retentionMillis;
    private final int maxRetries;
    private final RedisScript<Long> compareAndSet = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            if not previous then return 0 end
            if cjson.decode(previous).version ~= tonumber(ARGV[1]) then return 0 end
            redis.call('SET', KEYS[1], ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    public DraftStateManager(RedisTemplate<String, Object> redis, ObjectMapper mapper,
            @Value("${game.state.ttl:1800000}") long retentionMillis,
            @Value("${game.state.update.max-retries:5}") int maxRetries) {
        if (retentionMillis <= DraftState.DURATION_MILLIS || maxRetries < 1) {
            throw new IllegalArgumentException("Retenção deve superar 20s e tentativas devem ser positivas.");
        }
        this.redis = redis;
        this.mapper = mapper;
        this.retentionMillis = retentionMillis;
        this.maxRetries = maxRetries;
    }

    public void create(DraftState state) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(state.roomId()), json(state),
                retentionMillis, TimeUnit.MILLISECONDS))) {
            throw new IllegalStateException("Draft já existe para esta sala.");
        }
    }

    public Optional<DraftState> find(UUID roomId) {
        String value = (String) redis.opsForValue().get(key(roomId));
        if (value == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(value, DraftState.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Estado de Draft inválido no Redis.", exception);
        }
    }

    public List<UUID> findAllRoomIds() {
        Set<String> keys = redis.keys("draft:state:*");
        if (keys == null || keys.isEmpty()) return List.of();
        return keys.stream().map(key -> UUID.fromString(key.substring("draft:state:".length()))).toList();
    }

    public void delete(UUID roomId) {
        redis.delete(key(roomId));
    }

    // Mutator puro. Só o autor do CAS bem-sucedido recebe a transição para publicar os eventos.
    public Optional<Change> update(UUID roomId, UnaryOperator<DraftState> mutator) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            DraftState before = find(roomId).orElseThrow(() -> new NoSuchElementException("Draft não encontrado."));
            DraftState after = Objects.requireNonNull(mutator.apply(before));
            if (after.equals(before)) return Optional.empty();
            if (!roomId.equals(after.roomId()) || !before.roomCode().equals(after.roomCode())
                    || before.deadlineEpochMillis() != after.deadlineEpochMillis()
                    || !before.player1().playerId().equals(after.player1().playerId())
                    || !before.player2().playerId().equals(after.player2().playerId())) {
                throw new IllegalArgumentException("Update não pode trocar sala, jogadores ou prazo do Draft.");
            }
            after = after.withVersion(before.version() + 1);
            Long stored = redis.execute(compareAndSet, List.of(key(roomId)), Long.toString(before.version()),
                    json(after), Long.toString(retentionMillis));
            if (Long.valueOf(1).equals(stored)) return Optional.of(new Change(before, after));
        }
        throw new ConcurrentStateModificationException("Conflito ao atualizar o Draft da sala " + roomId);
    }

    private String key(UUID roomId) {
        return "draft:state:" + Objects.requireNonNull(roomId);
    }

    private String json(DraftState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o Draft.", exception);
        }
    }

    public record Change(DraftState before, DraftState after) { }
}
