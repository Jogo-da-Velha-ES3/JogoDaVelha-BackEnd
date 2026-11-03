package com.jogodavelha.auth;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class AuthPersistenceIT {
    @Container
    private static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            "postgres:16-alpine"
    ).withDatabaseName("be003_validation")
     .withUsername("be003_test")
     .withPassword("be003_test_only");

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @ComponentScan(basePackageClasses = AuthService.class)
    static class Config {
        private final String schema = "be003_" + UUID.randomUUID().toString().replace("-", "");

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(postgresContainer.getJdbcUrl()
                    + "?currentSchema=" + schema, postgresContainer.getUsername(), postgresContainer.getPassword());
        }

        @Bean
        Flyway flyway(DataSource ds) {
            Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                    .schemas(schema).defaultSchema(schema).cleanDisabled(true).baselineOnMigrate(false)
                    .load();
            flyway.migrate();
            return flyway;
        }

        @Bean
        @DependsOn("flyway")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("com.jogodavelha.auth");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(java.util.Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.default_schema", schema
            ));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Test
    void testUserPersistenceAndRetrieval() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var repo = context.getBean(UserRepository.class);
            var authService = context.getBean(AuthService.class);
            var passwordEncoder = context.getBean(PasswordEncoder.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            User user = tx.execute(status -> {
                RegisterRequest request = new RegisterRequest("testuser", "password123", "test@example.com");
                return authService.register(request);
            });

            assertNotNull(user.getId());
            assertEquals("testuser", user.getUsername());
            assertEquals("test@example.com", user.getEmail());
            assertEquals(0, user.getCoinsBalance());
            assertTrue(user.getPasswordHash().startsWith("$2"));
            assertNotEquals("password123", user.getPasswordHash());

            tx.executeWithoutResult(status -> {
                Optional<User> foundById = repo.findById(user.getId());
                assertTrue(foundById.isPresent());
                assertEquals("testuser", foundById.get().getUsername());
                assertEquals("test@example.com", foundById.get().getEmail());
                assertEquals(0, foundById.get().getCoinsBalance());
                assertTrue(passwordEncoder.matches("password123", foundById.get().getPasswordHash()));
            });

            tx.executeWithoutResult(status -> {
                Optional<User> foundByEmail = repo.findByEmail("test@example.com");
                assertTrue(foundByEmail.isPresent());
                assertEquals(user.getId(), foundByEmail.get().getId());
            });

            var flyway = context.getBean(Flyway.class);
            flyway.validate();
            assertEquals(0, flyway.migrate().migrationsExecuted);
        }
    }

    @Test
    void testUsernameCaseInsensitiveUniqueConstraint() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var repo = context.getBean(UserRepository.class);
            var authService = context.getBean(AuthService.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            tx.executeWithoutResult(status -> {
                RegisterRequest request1 = new RegisterRequest("TestUser", "password123", "test1@example.com");
                authService.register(request1);
            });

            assertThrows(UsernameAlreadyExistsException.class, () -> {
                tx.execute(status -> {
                    RegisterRequest request2 = new RegisterRequest("testuser", "password456", "test2@example.com");
                    return authService.register(request2);
                });
            });

            assertThrows(UsernameAlreadyExistsException.class, () -> {
                tx.execute(status -> {
                    RegisterRequest request3 = new RegisterRequest("TESTUSER", "password789", "test3@example.com");
                    return authService.register(request3);
                });
            });
        }
    }

    @Test
    void testEmailUniqueConstraint() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var repo = context.getBean(UserRepository.class);
            var authService = context.getBean(AuthService.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            tx.executeWithoutResult(status -> {
                RegisterRequest request1 = new RegisterRequest("user1", "password123", "test@example.com");
                authService.register(request1);
            });

            assertThrows(EmailAlreadyExistsException.class, () -> {
                tx.execute(status -> {
                    RegisterRequest request2 = new RegisterRequest("user2", "password456", "test@example.com");
                    return authService.register(request2);
                });
            });
        }
    }

    @Test
    void testPasswordHashStartsWithBCryptPrefix() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var authService = context.getBean(AuthService.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

            User user = tx.execute(status -> {
                RegisterRequest request = new RegisterRequest("testuser", "password123", "test@example.com");
                return authService.register(request);
            });

            assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));
        }
    }
}
