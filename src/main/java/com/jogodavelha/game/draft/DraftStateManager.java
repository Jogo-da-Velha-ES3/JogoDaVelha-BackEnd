package com.jogodavelha.game.draft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.logic.ConcurrentStateModificationException;
import com.jogodavelha.telemetry.ActionProfiler;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

@Component
public class DraftStateManager {
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper mapper;
    private final long retentionMillis;
    private final int maxRetries;
    /**
     * Último JSON commitado por este nó. Evita o GET antes do CAS no caminho feliz;
     * um conflito de versão ou um mutator que rejeita o estado em cache força releitura.
     * Cada entrada expira junto com a retenção do Redis; o mapa é limitado e entradas
     * expiradas são removidas na leitura e na poda disparada ao ultrapassar o limite.
     */
    private final ConcurrentHashMap<UUID, CacheEntry> committedStates = new ConcurrentHashMap<>();
    private static final int MAX_CACHED_STATES = 4096;
    private record CacheEntry(String json, long version, long expiresAtEpochMillis) { }
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
        remember(state.roomId(), json(state), state.version());
    }

    /** Entrada válida apenas se ainda não expirou; a expirada é removida na hora. */
    private CacheEntry liveEntry(UUID roomId) {
        CacheEntry entry = committedStates.get(roomId);
        if (entry != null && entry.expiresAtEpochMillis() <= System.currentTimeMillis()) {
            committedStates.remove(roomId, entry);
            return null;
        }
        return entry;
    }

    /**
     * Remove a entrada somente se ela não for mais nova que {@code version}: uma operação
     * que falhou no CAS sobre um estado antigo não pode derrubar o commit de outra thread.
     */
    private void evictIfNotNewer(UUID roomId, long version) {
        committedStates.computeIfPresent(roomId,
                (id, entry) -> entry.version() <= version ? null : entry);
    }

    private void remember(UUID roomId, String json, long version) {
        committedStates.merge(roomId,
                new CacheEntry(json, version, System.currentTimeMillis() + retentionMillis),
                (existing, incoming) -> incoming.version() >= existing.version() ? incoming : existing);
        if (committedStates.size() > MAX_CACHED_STATES) {
            long now = System.currentTimeMillis();
            committedStates.values().removeIf(entry -> entry.expiresAtEpochMillis() <= now);
            var iterator = committedStates.values().iterator();
            while (committedStates.size() > MAX_CACHED_STATES && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    public Optional<DraftState> find(UUID roomId) {
        String value = get(roomId);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(mapper.readValue(value, DraftState.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Estado de Draft inválido no Redis.", exception);
        }
    }

    private String get(UUID roomId) {
        long readStart = System.nanoTime();
        String value = (String) redis.opsForValue().get(key(roomId));
        ActionProfiler.add("draft.redis.get", readStart);
        return value;
    }

    public List<UUID> findAllRoomIds() {
        Set<String> keys = redis.keys("draft:state:*");
        if (keys == null || keys.isEmpty()) {
            committedStates.clear();
            return List.of();
        }
        List<UUID> ids = keys.stream().map(key -> UUID.fromString(key.substring("draft:state:".length()))).toList();
        Set<UUID> live = Set.copyOf(ids);
        committedStates.keySet().removeIf(id -> !live.contains(id));
        return ids;
    }

    public void delete(UUID roomId) {
        committedStates.remove(roomId);
        redis.delete(key(roomId));
    }

    // Mutator puro. Só o autor do CAS bem-sucedido recebe a transição para publicar os eventos.
    public Optional<Change> update(UUID roomId, UnaryOperator<DraftState> mutator) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            DraftState before;
            boolean fromCache = false;
            CacheEntry cached = liveEntry(roomId);
            if (cached != null) {
                try {
                    before = mapper.readValue(cached.json(), DraftState.class);
                    fromCache = true;
                } catch (JsonProcessingException exception) {
                    committedStates.remove(roomId, cached);
                    continue;
                }
            } else {
                String value = get(roomId);
                if (value == null) {
                    committedStates.remove(roomId);
                    throw new NoSuchElementException("Draft não encontrado.");
                }
                try {
                    before = mapper.readValue(value, DraftState.class);
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Estado de Draft inválido no Redis.", exception);
                }
            }
            DraftState after;
            try {
                after = Objects.requireNonNull(mutator.apply(before));
            } catch (RuntimeException failure) {
                // Rejeição sobre estado em cache pode ser defasada: relê e tenta de novo,
                // sem derrubar um commit mais novo feito por outra thread.
                if (fromCache) {
                    evictIfNotNewer(roomId, before.version());
                    continue;
                }
                throw failure;
            }
            if (after.equals(before)) return Optional.empty();
            if (!roomId.equals(after.roomId()) || !before.roomCode().equals(after.roomCode())
                    || before.deadlineEpochMillis() != after.deadlineEpochMillis()
                    || !before.player1().playerId().equals(after.player1().playerId())
                    || !before.player2().playerId().equals(after.player2().playerId())) {
                throw new IllegalArgumentException("Update não pode trocar sala, jogadores ou prazo do Draft.");
            }
            after = after.withVersion(before.version() + 1);
            String afterJson = json(after);
            long casStart = System.nanoTime();
            Long stored = redis.execute(compareAndSet, List.of(key(roomId)), Long.toString(before.version()),
                    afterJson, Long.toString(retentionMillis));
            ActionProfiler.add("draft.redis.cas", casStart);
            if (Long.valueOf(1).equals(stored)) {
                remember(roomId, afterJson, after.version());
                return Optional.of(new Change(before, after));
            }
            evictIfNotNewer(roomId, before.version());
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
