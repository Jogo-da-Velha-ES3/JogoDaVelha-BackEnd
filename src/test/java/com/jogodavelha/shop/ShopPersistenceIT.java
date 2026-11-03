package com.jogodavelha.shop;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.avatar.*;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterService;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.character.UserCharacter;
import com.jogodavelha.character.UserCharacterRepository;
import com.jogodavelha.coins.CoinTransactionRepository;
import com.jogodavelha.coins.CoinTransactionService;
import com.jogodavelha.coins.CoinTransactionType;
import com.jogodavelha.game.draft.InventoryDraftEligibilityProvider;
import com.jogodavelha.game.draft.DraftEligibilityProvider;
import com.jogodavelha.profile.ProfileService;
import com.jogodavelha.room.RoomPlayerService;
import com.jogodavelha.skill.SkillRepository;
import com.jogodavelha.skill.SkillService;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
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

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopPersistenceIT {
    private static PostgreSQLContainer<?> container;
    private static AnnotationConfigApplicationContext context;
    private static String jdbcUrl, username, password;
    private ShopService shop;
    private UserRepository users;
    private AvatarRepository avatars;
    private UserAvatarRepository possessions;
    private JdbcTemplate jdbc;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.jogodavelha")
    @Import({ShopService.class, AvatarService.class, RoomPlayerService.class, ProfileService.class,
            CharacterService.class, SkillService.class, InventoryDraftEligibilityProvider.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(jdbcUrl, username, password); }
        @Bean Flyway flyway(DataSource ds) {
            var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                    .cleanDisabled(true).baselineOnMigrate(false).load();
            flyway.migrate();
            return flyway;
        }
        @Bean @DependsOn("flyway")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("com.jogodavelha");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean CoinTransactionService transactions(CoinTransactionRepository repo) {
            return spy(new CoinTransactionService(repo));
        }
    }

    @BeforeAll
    static void startDisposableDatabase() {
        jdbcUrl = System.getProperty("be029.test.jdbc-url");
        if (jdbcUrl == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("be029_validation")
                    .withUsername("be029_test").withPassword("be029_test_only");
            container.start();
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
        } else {
            // Aceita somente a instância portátil de teste; não lê DB_URL nem o .env do projeto.
            if (!jdbcUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/be029_validation")) {
                throw new IllegalArgumentException("Use somente PostgreSQL descartável local be029_validation.");
            }
            username = "be029_test";
            password = "be029_test_only";
        }
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void stopDisposableDatabase() {
        if (context != null) context.close();
        if (container != null) container.stop();
    }

    @BeforeEach
    void setUp() {
        shop = context.getBean(ShopService.class);
        users = context.getBean(UserRepository.class);
        avatars = context.getBean(AvatarRepository.class);
        possessions = context.getBean(UserAvatarRepository.class);
        jdbc = context.getBean(JdbcTemplate.class);
    }

    @Test
    void simultaneousDoubleClickChargesAndAcquiresOnlyOnce() throws Exception {
        User user = user(200);
        Avatar avatar = avatar(80);
        List<String> results = concurrentPurchases(user.getId(), List.of(avatar.getId(), avatar.getId()));

        assertEquals(1, Collections.frequency(results, "OK"));
        assertEquals(1, Collections.frequency(results, "SHOP_ITEM_ALREADY_OWNED"));
        assertEquals(120, users.findById(user.getId()).orElseThrow().getCoinsBalance());
        assertEquals(1, possessions.findByUser_Id(user.getId()).size());
        assertEquals(1, count("coin_transactions", user.getId()));
        assertEquals("DEBIT", jdbc.queryForObject("select type from coin_transactions where user_id=?", String.class, user.getId()));
        assertEquals(120, jdbc.queryForObject("select balance_after from coin_transactions where user_id=?", Integer.class, user.getId()));
    }

    @Test
    void concurrentDifferentPurchasesCannotSpendTheSameBalanceTwice() throws Exception {
        User user = user(100);
        List<String> results = concurrentPurchases(user.getId(), List.of(avatar(80).getId(), avatar(80).getId()));

        assertEquals(1, Collections.frequency(results, "OK"));
        assertEquals(1, Collections.frequency(results, "INSUFFICIENT_COINS"));
        assertEquals(20, users.findById(user.getId()).orElseThrow().getCoinsBalance());
        assertEquals(1, possessions.findByUser_Id(user.getId()).size());
        assertEquals(1, count("coin_transactions", user.getId()));
    }

    @Test
    void ledgerFailureRollsBackBothTheDebitAndThePossession() {
        User user = user(100);
        Avatar avatar = avatar(77);
        CoinTransactionService transactions = context.getBean(CoinTransactionService.class);
        doThrow(new IllegalStateException("Falha de teste no extrato")).when(transactions)
                .registerDebit(any(), eq(77), anyString());
        try {
            assertThrows(IllegalStateException.class, () -> shop.purchase(user.getId(), ShopCategory.AVATARS, avatar.getId()));
        } finally {
            reset(transactions);
        }

        assertEquals(100, users.findById(user.getId()).orElseThrow().getCoinsBalance());
        assertEquals(0, count("user_avatars", user.getId()));
        assertEquals(0, count("coin_transactions", user.getId()));
    }

    @Test
    void avatarEquipmentIsSharedByProfileAndRoomWithoutChangingCharacterOrHistory() {
        User user = user(200);
        Avatar first = avatar(20), second = avatar(30);
        var characters = context.getBean(CharacterRepository.class);
        var character = characters.findBySymbol(CharacterSymbol.X).orElseThrow();
        context.getBean(UserCharacterRepository.class).save(new UserCharacter(user, character));
        shop.equip(user.getId(), ShopCategory.CHARACTERS, character.getId());
        shop.purchase(user.getId(), ShopCategory.AVATARS, first.getId());
        shop.purchase(user.getId(), ShopCategory.AVATARS, second.getId());
        shop.equip(user.getId(), ShopCategory.AVATARS, first.getId());
        shop.equip(user.getId(), ShopCategory.AVATARS, second.getId());

        assertEquals(second.getCode(), context.getBean(RoomPlayerService.class).info(user).avatar());
        var profile = context.getBean(ProfileService.class).getProfile(user.getId());
        assertEquals(second.getCode(), profile.avatar());
        assertNull(profile.preferredCharacter());
        var selected = context.getBean(UserEquipmentRepository.class).findById(user.getId()).orElseThrow();
        assertEquals(character.getId(), selected.getCharacterId());
        assertEquals(second.getId(), selected.getAvatarId());
        assertEquals(1, count("user_equipment", user.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from games where player1_id=? or player2_id=?",
                Integer.class, user.getId(), user.getId()));

        Avatar fallback = avatars.findAll().stream().filter(item -> item.getCode().equals(Avatar.DEFAULT_CODE)).findFirst().orElseThrow();
        shop.equip(user.getId(), ShopCategory.AVATARS, fallback.getId());
        assertEquals(Avatar.DEFAULT_CODE, context.getBean(RoomPlayerService.class).info(user).avatar());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "update user_equipment set avatar_id=? where user_id=?", avatar(10).getId(), user.getId()));
    }

    @Test
    void boughtCharactersAndSkillsAreAvailableToTheExistingDraftInventory() {
        User user = user(2000);
        var character = context.getBean(CharacterRepository.class).findBySymbol(CharacterSymbol.ESTRELA).orElseThrow();
        var skill = context.getBean(SkillRepository.class).findByCode("TORNADO").orElseThrow();
        shop.purchase(user.getId(), ShopCategory.CHARACTERS, character.getId());
        shop.purchase(user.getId(), ShopCategory.SKILLS, skill.getId());

        context.getBean(SkillService.class).getInventory(user.getId());
        var eligible = context.getBean(DraftEligibilityProvider.class).forPlayer(user.getId());
        assertTrue(eligible.characters().contains(character.getId()));
        assertTrue(eligible.skills().contains("TORNADO"));
        // O inventário cresce; a regra de duas habilidades continua no DraftState.Player.
        assertThrows(IllegalArgumentException.class, () -> new com.jogodavelha.game.draft.DraftState.Player(
                user.getId(), eligible.characters(), eligible.skills(), character.getId(),
                List.of("BOMBA", "ESCUDO", "TORNADO")));
    }

    private User user(int coins) {
        String name = "shop_" + UUID.randomUUID().toString().substring(0, 15);
        User user = new User(name, "test_hash", name + "@example.com");
        user.setCoinsBalance(coins);
        return users.saveAndFlush(user);
    }

    private Avatar avatar(int price) {
        return avatars.saveAndFlush(new Avatar("TEST_" + UUID.randomUUID(), "Avatar de teste", "", price));
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject("select count(*) from " + table + " where user_id=?", Integer.class, userId);
    }

    private List<String> concurrentPurchases(UUID userId, List<UUID> itemIds) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = itemIds.stream().map(id -> executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { shop.purchase(userId, ShopCategory.AVATARS, id); return "OK"; }
                catch (ShopException error) { return error.getCode(); }
            })).toList();
            return List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
        }
    }
}
