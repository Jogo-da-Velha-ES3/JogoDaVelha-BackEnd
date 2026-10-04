package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.GameRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RoomPersistenceIT {
    @Container
    private static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            "postgres:16-alpine"
    ).withDatabaseName("be007_validation")
     .withUsername("be007_test")
     .withPassword("be007_test_only");

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = RoomRepository.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(postgresContainer.getJdbcUrl(),
                    postgresContainer.getUsername(), postgresContainer.getPassword());
        }
        @Bean Flyway flyway(DataSource ds) {
            Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:be007-no-sql")
                    .cleanDisabled(true).baselineOnMigrate(false)
                    .javaMigrations(new V1__TestUsers(), new V2__TestRooms())
                    .load();
            flyway.migrate();
            return flyway;
        }
        @Bean @DependsOn("flyway")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("com.jogodavelha.auth", "com.jogodavelha.room");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean RoomCodeAllocator allocator(RoomRepository repo, JdbcTemplate jdbc) {
            return new RoomCodeAllocator(repo, org.mockito.Mockito.mock(GameRepository.class), jdbc);
        }
    }

    // Cria users apenas para o teste, não para a aplicação.
    public static class V1__TestUsers extends BaseJavaMigration {
        public void migrate(Context context) throws Exception {
            try (var statement = context.getConnection().createStatement()) {
                statement.execute("""
                    CREATE TABLE users (
                        id uuid PRIMARY KEY,
                        username varchar(30) NOT NULL,
                        email varchar(255) NOT NULL,
                        password_hash varchar(255) NOT NULL,
                        coins_balance integer NOT NULL DEFAULT 0,
                        created_at timestamptz NOT NULL DEFAULT now()
                    )
                """);
            }
        }
    }
    public static class V2__TestRooms extends BaseJavaMigration {
        public void migrate(Context context) throws Exception {
            try (var statement = context.getConnection().createStatement()) {
                statement.execute("""
                    CREATE TABLE rooms (
                        id uuid PRIMARY KEY,
                        code varchar(4) NOT NULL CHECK (code ~ '^[0-9]{4}$'),
                        status varchar(16) NOT NULL CHECK (status IN ('WAITING', 'IN_GAME', 'CLOSED')),
                        player1_id uuid NOT NULL REFERENCES users(id),
                        player2_id uuid REFERENCES users(id),
                        CHECK (player2_id IS NULL OR player1_id <> player2_id),
                        CHECK (status <> 'IN_GAME' OR player2_id IS NOT NULL)
                    )
                """);
                statement.execute("CREATE UNIQUE INDEX rooms_active_code_unique ON rooms(code) WHERE status IN ('WAITING', 'IN_GAME')");
            }
        }
    }

    @Test void validatesJpaConstraintsAndConcurrentReservations() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var repo = context.getBean(RoomRepository.class);
            var allocator = context.getBean(RoomCodeAllocator.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            UUID oneId = UUID.randomUUID();
            UUID twoId = UUID.randomUUID();
            jdbc.update("INSERT INTO users(id, username, email, password_hash) VALUES (?, ?, ?, ?), (?, ?, ?, ?)",
                oneId, "one", "one@example.com", "hash1",
                twoId, "two", "two@example.com", "hash2");
            User one = new User("one", null, null);
            one.setId(oneId);
            User two = new User("two", null, null);
            two.setId(twoId);
            Room saved = repo.saveAndFlush(new Room("0042", one));
            tx.executeWithoutResult(status -> {
                Room loaded = repo.findById(saved.getId()).orElseThrow();
                assertEquals("0042", loaded.getCode());
                assertEquals(oneId, loaded.getPlayer1().getId());
                assertNull(loaded.getPlayer2());
            });
            assertSqlState("23505", () -> repo.saveAndFlush(new Room("0042", one)));
            assertSqlState("23503", () -> jdbc.update("INSERT INTO rooms(id,code,status,player1_id) VALUES (?, '0043','WAITING',?)", UUID.randomUUID(), UUID.randomUUID()));
            assertSqlState("23514", () -> jdbc.update("UPDATE rooms SET player2_id=? WHERE id=?", oneId, saved.getId()));
            assertSqlState("23514", () -> jdbc.update("UPDATE rooms SET status='IN_GAME' WHERE id=?", saved.getId()));
            assertSqlState("23514", () -> jdbc.update("UPDATE rooms SET code='ABCD' WHERE id=?", saved.getId()));
            jdbc.update("UPDATE rooms SET player2_id=?, status='IN_GAME' WHERE id=?", twoId, saved.getId());
            assertSqlState("23505", () -> repo.saveAndFlush(new Room("0042", one)));
            jdbc.update("UPDATE rooms SET status='CLOSED' WHERE id=?", saved.getId());
            assertNotNull(repo.saveAndFlush(new Room("0042", one)).getId());

            var flyway = context.getBean(Flyway.class);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
        }
    }

    private static void assertSqlState(String state, org.junit.jupiter.api.function.Executable action) {
        Throwable error = assertThrows(Exception.class, action);
        while (error != null && !(error instanceof SQLException)) error = error.getCause();
        assertNotNull(error, "Expected a PostgreSQL constraint error");
        assertEquals(state, ((SQLException) error).getSQLState());
    }
}
