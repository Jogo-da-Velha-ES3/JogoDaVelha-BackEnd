package com.jogodavelha.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;

@Component
public class RematchStateManager {
    private static final String KEY_PREFIX = "room:rematch:";
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper mapper;
    private final long retentionMillis;
    private final RedisScript<Long> begin = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            if previous then
                local status = cjson.decode(previous).status
                if status == 'PENDING' then return 0 end
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
            """, Long.class);
    private final RedisScript<Long> compareAndSet = new DefaultRedisScript<>("""
            local previous = redis.call('GET', KEYS[1])
            if not previous then return 0 end
            local current = cjson.decode(previous)
            if current.version ~= tonumber(ARGV[1]) or current.status ~= 'PENDING'
                    or current.gameId ~= ARGV[2] or current.deadlineEpochMillis ~= tonumber(ARGV[3]) then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[4], 'PX', ARGV[5])
            return 1
            """, Long.class);
    private final RedisScript<Long> releaseLock = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    public RematchStateManager(RedisTemplate<String, Object> redis, ObjectMapper mapper,
                               @Value("${game.rematch.state-ttl:86400000}") long retentionMillis) {
        if (retentionMillis <= 15_000) throw new IllegalArgumentException("Retenção da revanche inválida.");
        this.redis = redis;
        this.mapper = mapper;
        this.retentionMillis = retentionMillis;
    }

    public Optional<RematchState> find(UUID roomId) {
        String value = (String) redis.opsForValue().get(key(roomId));
        if (value == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(value, RematchState.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Estado de revanche inválido no Redis.", exception);
        }
    }

    public boolean begin(RematchState state) {
        Long result = redis.execute(begin, List.of(key(state.roomId())), json(state),
                Long.toString(retentionMillis));
        return Long.valueOf(1).equals(result);
    }

    public Optional<RematchState> transition(RematchState expected, RematchState.Status target) {
        if (expected.status() != RematchState.Status.PENDING || target == RematchState.Status.PENDING) {
            throw new IllegalArgumentException("Transição de revanche inválida.");
        }
        RematchState updated = expected.withStatus(target);
        Long result = redis.execute(compareAndSet, List.of(key(expected.roomId())),
                Long.toString(expected.version()), expected.gameId().toString(),
                Long.toString(expected.deadlineEpochMillis()), json(updated), Long.toString(retentionMillis));
        if (Long.valueOf(1).equals(result)) return Optional.of(updated);
        return Optional.empty();
    }

    public boolean acquireInitialization(UUID roomId, String token) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                initializationKey(roomId), token, Duration.ofSeconds(30)));
    }

    public void releaseInitialization(UUID roomId, String token) {
        redis.execute(releaseLock, List.of(initializationKey(roomId)), token);
    }

    public List<UUID> findAllRoomIds() {
        List<UUID> result = new ArrayList<>();
        try (var keys = redis.scan(ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build())) {
            while (keys.hasNext()) {
                String key = keys.next();
                if (!key.startsWith(KEY_PREFIX + "initializing:")) {
                    result.add(UUID.fromString(key.substring(KEY_PREFIX.length())));
                }
            }
        }
        return result;
    }

    private String key(UUID roomId) {
        return KEY_PREFIX + roomId;
    }

    private String initializationKey(UUID roomId) {
        return KEY_PREFIX + "initializing:" + roomId;
    }

    private String json(RematchState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar o estado da revanche.", exception);
        }
    }
}
