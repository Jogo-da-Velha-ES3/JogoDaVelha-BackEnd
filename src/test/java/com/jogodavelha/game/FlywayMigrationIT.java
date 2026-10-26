package com.jogodavelha.game;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class FlywayMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("be038_flyway")
            .withUsername("be038_test")
            .withPassword("be038_test_only");

    @Test
    void cleanDatabaseMigratesThroughV20WithGameCharacterColumns() {
        String schema = "be038_" + UUID.randomUUID().toString().replace("-", "");
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl() + "?currentSchema=" + schema,
                postgres.getUsername(),
                postgres.getPassword());

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(true)
                .load();

        MigrateResult first = flyway.migrate();
        assertTrue(first.success);
        assertTrue(first.migrationsExecuted >= 20);

        MigrateResult second = flyway.migrate();
        assertEquals(0, second.migrationsExecuted);
        flyway.validate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer characterColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'games'
                  AND column_name IN ('player1_character_id', 'player2_character_id')
                """, Integer.class, schema);
        assertEquals(2, characterColumns);

        List<String> gameIndexes = jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE tablename = 'games' AND schemaname = ?
                ORDER BY indexname
                """, String.class, schema);
        assertTrue(gameIndexes.stream().anyMatch(name -> name.contains("one_active") || name.contains("room")),
                "Expected active-game-per-room index, found: " + gameIndexes);

        String flywayV20 = jdbc.queryForObject("""
                SELECT script FROM flyway_schema_history WHERE version = '20'
                """, String.class);
        assertNotNull(flywayV20);
        assertTrue(flywayV20.contains("one_active_game_per_room"));

        Integer userStatsTable = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = ? AND table_name = 'user_stats'
                """, Integer.class, schema);
        assertEquals(1, userStatsTable);
    }
}
