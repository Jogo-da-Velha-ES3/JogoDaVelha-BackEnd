package com.jogodavelha.skill;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class SkillCodePersistenceIT {
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("skill_codes")
            .withUsername("skill_codes_test")
            .withPassword("skill_codes_test_only");

    @Configuration
    static class Config {
        private final String schema = "skill_codes_" + UUID.randomUUID().toString().replace("-", "");

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(postgres.getJdbcUrl() + "&currentSchema=" + schema,
                    postgres.getUsername(), postgres.getPassword());
        }

        @Bean
        Flyway flyway(DataSource dataSource) {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
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
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    @Test
    void migrationsPopulateExpectedCodesAndEnforceCodeConstraints() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            List<String> codes = jdbc.queryForList("SELECT code FROM skills ORDER BY code", String.class);

            assertEquals(List.of(
                    "AMPULHETA",
                    "BOMBA",
                    "BURACO_NEGRO",
                    "CAMPO_DE_FORCA",
                    "CONGELAR",
                    "ESCUDO",
                    "REFORCO",
                    "SILENCIO",
                    "TORNADO",
                    "TROCA_RAPIDA"
            ), codes);

            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO skills (name, code, effect_category, description)
                    VALUES ('OUTRA HABILIDADE', 'BOMBA', 'DEFENSE', 'Teste de código duplicado')
                    """));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO skills (name, code, effect_category, description)
                    VALUES ('CODIGO COM ESPACO', 'CODIGO COM ESPACO', 'DEFENSE', 'Teste de espaço')
                    """));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO skills (name, code, effect_category, description)
                    VALUES ('CODIGO MINUSCULO', 'codigo_minusculo', 'DEFENSE', 'Teste de minúsculas')
                    """));
        }
    }
}
