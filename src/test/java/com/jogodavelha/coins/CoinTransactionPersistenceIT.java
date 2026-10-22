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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import com.jogodavelha.auth.User;

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
    @ComponentScan(basePackageClasses = {
            CoinTransactionService.class,
            CoinService.class
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

        try (var context =
                     new AnnotationConfigApplicationContext(
                             Config.class
                     )) {

            var transactionRepository =
                    context.getBean(CoinTransactionRepository.class);

            var transactionService =
                    context.getBean(CoinTransactionService.class);

            var tx =
                    new TransactionTemplate(
                            context.getBean(
                                    PlatformTransactionManager.class
                            )
                    );

            User user = tx.execute(status -> {
                User created =
                        new User(
                                "player1",
                                "hash",
                                "player1@test.com"
                        );

                return context.getBean(UserRepository.class)
                        .save(created);
            });

            tx.executeWithoutResult(status -> {
                user.addCoins(50);

                transactionService.registerCredit(
                        user,
                        50,
                        "MATCH_WIN"
                );
            });

            var transactions =
                    tx.execute(status ->
                            transactionRepository
                                    .findByUserOrderByCreatedAtDesc(user)
                    );

            assertNotNull(transactions);
            assertEquals(1, transactions.size());

            CoinTransaction transaction =
                    transactions.get(0);

            assertEquals(
                    CoinTransactionType.CREDIT,
                    transaction.getType()
            );

            assertEquals(
                    50,
                    transaction.getAmount()
            );

            assertEquals(
                    "MATCH_WIN",
                    transaction.getSource()
            );
        }
    }

    @Test
    void persistsBalanceAfterCorrectly() {

        try (var context =
                     new AnnotationConfigApplicationContext(
                             Config.class
                     )) {

            var transactionRepository =
                    context.getBean(CoinTransactionRepository.class);

            var transactionService =
                    context.getBean(CoinTransactionService.class);

            var tx =
                    new TransactionTemplate(
                            context.getBean(
                                    PlatformTransactionManager.class
                            )
                    );

            User user = tx.execute(status -> {
                User created =
                        new User(
                                "player2",
                                "hash",
                                "player2@test.com"
                        );

                return context.getBean(UserRepository.class)
                        .save(created);
            });

            tx.executeWithoutResult(status -> {
                user.addCoins(50);

                transactionService.registerCredit(
                        user,
                        50,
                        "MATCH_WIN"
                );
            });

            CoinTransaction transaction =
                    tx.execute(status ->
                            transactionRepository
                                    .findByUserOrderByCreatedAtDesc(user)
                                    .get(0)
                    );

            assertNotNull(transaction);

            assertEquals(
                    50,
                    transaction.getBalanceAfter()
            );

            assertEquals(
                    50,
                    transaction.getAmount()
            );

            assertEquals(
                    CoinTransactionType.CREDIT,
                    transaction.getType()
            );
        }
    }

    @Test
    void rewardsRemainIdempotentInDatabase() {

        try (var context =
                     new AnnotationConfigApplicationContext(
                             Config.class
                     )) {

            var transactionRepository =
                    context.getBean(CoinTransactionRepository.class);

            var transactionService =
                    context.getBean(CoinTransactionService.class);

            var tx =
                    new TransactionTemplate(
                            context.getBean(
                                    PlatformTransactionManager.class
                            )
                    );

            User user = tx.execute(status -> {
                User created =
                        new User(
                                "player3",
                                "hash",
                                "player3@test.com"
                        );

                return context.getBean(UserRepository.class)
                        .save(created);
            });

            tx.executeWithoutResult(status -> {
                user.addCoins(50);

                transactionService.registerCredit(
                        user,
                        50,
                        "MATCH_WIN"
                );
            });

            var firstRead =
                    tx.execute(status ->
                            transactionRepository
                                    .findByUserOrderByCreatedAtDesc(user)
                    );

            assertNotNull(firstRead);
            assertEquals(1, firstRead.size());

            CoinTransaction transaction =
                    firstRead.get(0);

            assertEquals(
                    CoinTransactionType.CREDIT,
                    transaction.getType()
            );

            assertEquals(
                    50,
                    transaction.getAmount()
            );

            assertEquals(
                    50,
                    transaction.getBalanceAfter()
            );

            assertEquals(
                    "MATCH_WIN",
                    transaction.getSource()
            );

            var secondRead =
                    tx.execute(status ->
                            transactionRepository
                                    .findByUserOrderByCreatedAtDesc(user)
                    );

            assertNotNull(secondRead);

            assertEquals(
                    1,
                    secondRead.size()
            );
        }
    }
}
