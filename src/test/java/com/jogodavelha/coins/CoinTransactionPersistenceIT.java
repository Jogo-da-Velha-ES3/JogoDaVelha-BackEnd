package com.jogodavelha.coins;

import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.room.RoomRepository;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

@Testcontainers
class CoinTransactionPersistenceIT {

    @Container
    private static final PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("coins_validation")
                    .withUsername("coins_test")
                    .withPassword("coins_test_only");

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = {
            UserRepository.class,
            RoomRepository.class,
            GameRepository.class,
            CoinTransactionRepository.class
    })
    static class Config {

        private final String schema = "coins_" +
                UUID.randomUUID().toString().replace("-", "");

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    postgresContainer.getJdbcUrl()
                            + "?currentSchema=" + schema,
                    postgresContainer.getUsername(),
                    postgresContainer.getPassword()
            );
        }

        @Bean
        Flyway flyway(DataSource ds) {
            Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .schemas(schema)
                    .defaultSchema(schema)
                    .cleanDisabled(true)
                    .load();

            flyway.migrate();
            return flyway;
        }

        @Bean
        @DependsOn("flyway")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
                DataSource ds
        ) {
            var factory =
                    new LocalContainerEntityManagerFactoryBean();

            factory.setDataSource(ds);

            factory.setPackagesToScan(
                    "com.jogodavelha.auth",
                    "com.jogodavelha.room",
                    "com.jogodavelha.game",
                    "com.jogodavelha.coins"
            );

            factory.setJpaVendorAdapter(
                    new HibernateJpaVendorAdapter()
            );

            factory.setJpaPropertyMap(java.util.Map.of(
                    "hibernate.hbm2ddl.auto", "validate",
                    "hibernate.default_schema", schema
            ));

            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(
                EntityManagerFactory emf
        ) {
            return new JpaTransactionManager(emf);
        }
    }

    @Test
    void persistsCoinTransactionsWhenRewardsAreGranted() {
    }

    @Test
    void persistsBalanceAfterCorrectly() {
    }

    @Test
    void rewardsRemainIdempotentInDatabase() {
    }
}
