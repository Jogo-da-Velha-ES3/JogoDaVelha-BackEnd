package com.jogodavelha.game.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class GameStateManagerIT {

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    private RedisTemplate<String, Object> redisTemplate;
    private GameStateManager gameStateManager;
    private ObjectMapper objectMapper;
    private UUID gameId;
    private UUID player1Id;
    private UUID player2Id;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redisContainer.getHost(),
                redisContainer.getMappedPort(6379)
        );
        
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
        
        gameStateManager = new GameStateManager(redisTemplate, objectMapper, 1800000, 5);
        
        gameId = UUID.randomUUID();
        player1Id = UUID.randomUUID();
        player2Id = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @Test
    void testCreateAndFind() {
        gameStateManager.create(gameId, player1Id, 10000);
        
        var gameStateOpt = gameStateManager.find(gameId);
        
        assertTrue(gameStateOpt.isPresent());
        GameState state = gameStateOpt.get();
        assertEquals(gameId, state.getGameId());
        assertEquals(GameState.EMPTY_BOARD, state.getBoard());
        assertEquals(player1Id, state.getFirstPlayerId());
        assertEquals(player1Id, state.getCurrentTurnPlayerId());
        assertEquals(0L, state.getVersion());
    }

    @Test
    void testCreateFailsIfAlreadyExists() {
        gameStateManager.create(gameId, player1Id, 10000);
        
        assertThrows(IllegalStateException.class, () -> {
            gameStateManager.create(gameId, player1Id, 10000);
        });
    }

    @Test
    void testFindReturnsEmptyWhenNotExists() {
        var gameStateOpt = gameStateManager.find(UUID.randomUUID());
        
        assertFalse(gameStateOpt.isPresent());
    }

    @Test
    void testUpdateSuccessfully() {
        gameStateManager.create(gameId, player1Id, 10000);
        
        gameStateManager.update(gameId, state -> {
            state.setBoard("X---------------");
            state.setCurrentTurnPlayerId(player2Id);
            return state;
        });
        
        var gameStateOpt = gameStateManager.find(gameId);
        assertTrue(gameStateOpt.isPresent());
        GameState state = gameStateOpt.get();
        assertEquals("X---------------", state.getBoard());
        assertEquals(player2Id, state.getCurrentTurnPlayerId());
        assertEquals(1L, state.getVersion());
    }

    @Test
    void testUpdateWhenNotExistsThrowsException() {
        assertThrows(GameStateNotFoundException.class, () -> {
            gameStateManager.update(gameId, state -> state);
        });
    }

    @Test
    void testUpdateWithConflictRetriesAndSucceeds() throws InterruptedException {
        gameStateManager.create(gameId, player1Id, 10000);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    gameStateManager.update(gameId, state -> {
                        state.setBoard(state.getBoard() + threadId);
                        return state;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(2, successCount.get());
        
        var gameStateOpt = gameStateManager.find(gameId);
        assertTrue(gameStateOpt.isPresent());
        GameState state = gameStateOpt.get();
        assertEquals(2L, state.getVersion());
    }

    @Test
    void testUpdateExceedsMaxRetriesThrowsException() throws InterruptedException {
        gameStateManager.create(gameId, player1Id, 10000);
        
        GameStateManager lowRetryManager = new GameStateManager(redisTemplate, objectMapper, 1800000, 1);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lowRetryManager.update(gameId, state -> {
                        state.setBoard(state.getBoard() + threadId);
                        return state;
                    });
                    successCount.incrementAndGet();
                } catch (ConcurrentStateModificationException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertTrue(successCount.get() > 0);
        assertTrue(failureCount.get() > 0);
    }

    @Test
    void testDelete() {
        gameStateManager.create(gameId, player1Id, 10000);
        
        gameStateManager.delete(gameId);
        
        var gameStateOpt = gameStateManager.find(gameId);
        assertFalse(gameStateOpt.isPresent());
    }

    @Test
    void testTtlSetOnCreate() {
        gameStateManager.create(gameId, player1Id, 10000);
        
        Long ttl = redisTemplate.getExpire("game:state:" + gameId, TimeUnit.SECONDS);
        
        assertNotNull(ttl);
        assertTrue(ttl > 0);
        assertTrue(ttl <= 1800);
    }

    @Test
    void testTtlRenewedOnUpdate() {
        GameStateManager shortTtlManager = new GameStateManager(redisTemplate, objectMapper, 5000, 5);
        shortTtlManager.create(gameId, player1Id, 10000);
        
        Long initialTtl = redisTemplate.getExpire("game:state:" + gameId, TimeUnit.SECONDS);
        assertTrue(initialTtl > 0);
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        shortTtlManager.update(gameId, state -> {
            state.setBoard("X---------------");
            return state;
        });
        
        Long renewedTtl = redisTemplate.getExpire("game:state:" + gameId, TimeUnit.SECONDS);
        assertTrue(renewedTtl > 0);
        assertTrue(renewedTtl >= initialTtl - 2);
    }

    @Test
    void testTtlExpires() {
        GameStateManager shortTtlManager = new GameStateManager(redisTemplate, objectMapper, 1000, 5);
        shortTtlManager.create(gameId, player1Id, 10000);
        
        var gameStateOpt = gameStateManager.find(gameId);
        assertTrue(gameStateOpt.isPresent());
        
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        var afterExpiryOpt = gameStateManager.find(gameId);
        assertFalse(afterExpiryOpt.isPresent());
    }

    @Test
    void testConcurrentUpdateWithTwoThreads() throws InterruptedException {
        gameStateManager.create(gameId, player1Id, 10000);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    gameStateManager.update(gameId, state -> {
                        state.setBoard(state.getBoard() + threadId);
                        return state;
                    });
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals(2, successCount.get());
        
        var gameStateOpt = gameStateManager.find(gameId);
        assertTrue(gameStateOpt.isPresent());
        GameState state = gameStateOpt.get();
        assertEquals(2L, state.getVersion());
    }

    @Test
    void testDeserializationToleratesNewField() {
        gameStateManager.create(gameId, player1Id, 10000);
        
        String key = "game:state:" + gameId;
        String jsonWithNewField = String.format("""
            {
                "gameId": "%s",
                "board": "----------------",
                "firstPlayerId": "%s",
                "currentTurnPlayerId": "%s",
                "turnDurationMillis": 10000,
                "turnDeadlineEpochMillis": %d,
                "roundNumber": 1,
                "suddenDeath": false,
                "version": 0,
                "newFutureField": "some value"
            }
            """, gameId, player1Id, player1Id, System.currentTimeMillis() + 10000);
        
        redisTemplate.opsForValue().set(key, jsonWithNewField);
        
        var gameStateOpt = gameStateManager.find(gameId);
        assertTrue(gameStateOpt.isPresent());
        assertEquals(gameId, gameStateOpt.get().getGameId());
    }

    @Test
    void testDeserializationToleratesMissingField() {
        gameStateManager.create(gameId, player1Id, 10000);
        
        String key = "game:state:" + gameId;
        String jsonWithMissingField = String.format("""
            {
                "gameId": "%s",
                "board": "----------------",
                "firstPlayerId": "%s",
                "currentTurnPlayerId": "%s",
                "turnDurationMillis": 10000,
                "turnDeadlineEpochMillis": %d,
                "roundNumber": 1,
                "suddenDeath": false,
                "version": 0
            }
            """, gameId, player1Id, player1Id, System.currentTimeMillis() + 10000);
        
        redisTemplate.opsForValue().set(key, jsonWithMissingField);
        
        var gameStateOpt = gameStateManager.find(gameId);
        assertTrue(gameStateOpt.isPresent());
        assertEquals(gameId, gameStateOpt.get().getGameId());
    }
}
