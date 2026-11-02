package com.jogodavelha.player;

import com.jogodavelha.game.service.PlayerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PlayerServiceIT {
    @Container
    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, Object> redis;
    private PlayerService players;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new RedisTemplate<>();
        redis.setConnectionFactory(connectionFactory);
        redis.setKeySerializer(new StringRedisSerializer());
        redis.setValueSerializer(new StringRedisSerializer());
        redis.afterPropertiesSet();
        players = new PlayerService(redis, 1_000);
    }

    @AfterEach
    void closeConnection() {
        connectionFactory.destroy();
    }

    @Test
    void renewsActivePresenceTtlThenExpiresWithoutChangingGameState() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        String presenceKey = "presence:room:0042:player:" + playerId;
        String gameStateKey = "game:state:" + UUID.randomUUID();
        redis.opsForValue().set(gameStateKey, "authoritative-game-state", Duration.ofSeconds(10));

        assertFalse(players.markConnected("0042", playerId));
        Thread.sleep(600);
        players.refreshConnected("0042", playerId);

        Long refreshedTtl = redis.getExpire(presenceKey, TimeUnit.MILLISECONDS);
        assertNotNull(refreshedTtl);
        assertTrue(refreshedTtl > 700);
        Thread.sleep(600);
        assertTrue(players.isConnected("0042", playerId));
        Thread.sleep(500);

        assertFalse(players.isConnected("0042", playerId));
        assertEquals("authoritative-game-state", redis.opsForValue().get(gameStateKey));
    }
}
