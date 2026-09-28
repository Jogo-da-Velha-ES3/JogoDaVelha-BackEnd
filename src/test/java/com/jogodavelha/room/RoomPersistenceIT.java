package com.jogodavelha.room;

import com.jogodavelha.auth.User;
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

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executar com -Dtest=RoomPersistenceIT e -Droom.test.port=<porta do banco descartável>. */
class RoomPersistenceIT {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = RoomRepository.class)
    static class Config {
        private final String schema = "be007_" + UUID.randomUUID().toString().replace("-", "");
        @Bean DataSource dataSource() {
            String port = System.getProperty("room.test.port", "");
            if (!port.matches("[0-9]{1,5}") || port.equals("5432")) {
                throw new IllegalStateException("Use a dedicated disposable PostgreSQL port, never 5432.");
            }
            return new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:" + port
                    + "/be007_validation?currentSchema=" + schema, "be007_test", "be007_test_only");
        }
        @Bean Flyway flyway(DataSource ds) {
            Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:be007-no-sql")
                    .schemas(schema).defaultSchema(schema).cleanDisabled(true).baselineOnMigrate(false)
                    .javaMigrations(new V1__TestUsers(), new V2__TestRooms()).load();
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
            return new RoomCodeAllocator(repo, jdbc);
        }
    }

    // Cria users apenas para o teste, não para a aplicação.
    public static class V1__TestUsers extends BaseJavaMigration {
        public void migrate(Context context) throws Exception {
            try (var statement = context.getConnection().createStatement()) {
                statement.execute("CREATE TABLE users (id varchar(255) PRIMARY KEY, username varchar(255), password varchar(255), email varchar(255))");
            }
        }
    }
    public static class V2__TestRooms extends BaseJavaMigration {
        public void migrate(Context context) throws Exception {
            try (var statement = context.getConnection().createStatement()) {
                statement.execute(Files.readString(Path.of("docs/room/create_rooms.sql.example")));
            }
        }
    }

    @Test void validatesJpaConstraintsAndConcurrentReservations() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var repo = context.getBean(RoomRepository.class);
            var allocator = context.getBean(RoomCodeAllocator.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            jdbc.update("INSERT INTO users(id) VALUES ('one'), ('two')");
            User one = new User("one", null, null);
            Room saved = repo.saveAndFlush(new Room("0042", one));
            tx.executeWithoutResult(status -> {
                Room loaded = repo.findById(saved.getId()).orElseThrow();
                assertEquals("0042", loaded.getCode());
                assertEquals("one", loaded.getPlayer1().getId());
                assertNull(loaded.getPlayer2());
            });
            assertSqlState("23505", () -> repo.saveAndFlush(new Room("0042", one)));
            assertSqlState("23503", () -> jdbc.update("INSERT INTO rooms(id,code,status,player1_id) VALUES (?, '0043','WAITING','missing')", UUID.randomUUID()));
            assertSqlState("23514", () -> jdbc.update("UPDATE rooms SET player2_id='one' WHERE id=?", saved.getId()));
            assertSqlState("23514", () -> jdbc.update("UPDATE rooms SET status='IN_GAME' WHERE id=?", saved.getId()));
            assertSqlState("23514", () -> jdbc.update("UPDATE rooms SET code='ABCD' WHERE id=?", saved.getId()));
            jdbc.update("UPDATE rooms SET player2_id='two', status='IN_GAME' WHERE id=?", saved.getId());
            assertSqlState("23505", () -> repo.saveAndFlush(new Room("0042", one)));
            jdbc.update("UPDATE rooms SET status='CLOSED' WHERE id=?", saved.getId());
            assertNotNull(repo.saveAndFlush(new Room("0042", one)).getId());

            // As duas reservas vão disputar o único código livre.
            jdbc.update("INSERT INTO rooms(id,code,status,player1_id) SELECT gen_random_uuid(), lpad(n::text,4,'0'),'WAITING','one' FROM generate_series(0,9999) n WHERE n <> 9999 AND n <> 42");
            var gate = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                Callable<Boolean> request = () -> {
                    gate.await();
                    try { allocator.reserve(one); return true; }
                    catch (IllegalStateException exhausted) {
                        assertEquals("Não há códigos de sala disponíveis.", exhausted.getMessage());
                        return false;
                    }
                };
                var first = workers.submit(request);
                var second = workers.submit(request);
                gate.countDown();
                assertNotEquals(first.get(120, TimeUnit.SECONDS), second.get(120, TimeUnit.SECONDS));
            }
            assertEquals(10000, jdbc.queryForObject("SELECT count(*) FROM rooms WHERE status <> 'CLOSED'", Integer.class));
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
