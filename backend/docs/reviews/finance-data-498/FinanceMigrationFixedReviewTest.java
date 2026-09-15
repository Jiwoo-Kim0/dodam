package com.ssafy.dodam.domain.finance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import static org.junit.jupiter.api.Assertions.*;

class FinanceMigrationFixedReviewTest {
    @TempDir Path initialMigrations;

    private String prepareV13(String database) throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:33364/?allowPublicKeyRetrieval=true&useSSL=false", "root", "review-only-password");
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + database);
        }
        try (var paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".sql")
                    && !p.getFileName().toString().startsWith("V4__")).toList()) {
                Files.copy(path, initialMigrations.resolve(path.getFileName()));
            }
        }
        String url = "jdbc:mysql://127.0.0.1:33364/" + database + "?allowPublicKeyRetrieval=true&useSSL=false";
        Flyway initial = Flyway.configure().dataSource(url, "root", "review-only-password")
                .locations("filesystem:" + initialMigrations.toAbsolutePath()).load();
        initial.migrate();
        assertEquals("13", initial.info().current().getVersion().toString());
        assertEquals(7, initial.info().applied().length);
        try (var connection = DriverManager.getConnection(url, "root", "review-only-password");
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE review_existing_data (id INT PRIMARY KEY, marker VARCHAR(20))");
            statement.executeUpdate("INSERT INTO review_existing_data VALUES (1, 'preserve-me')");
        }
        return url;
    }

    @Test void localProfileUpgradesExistingV13AndPreservesData() throws Exception { verifyProfile("local"); }
    @Test void prodProfileUpgradesExistingV13AndPreservesData() throws Exception { verifyProfile("prod"); }

    private void verifyProfile(String profile) throws Exception {
        String url = prepareV13("review_fixed_" + profile);
        var sources = new YamlPropertySourceLoader().load(profile,
                new FileSystemResource("src/main/resources/application-" + profile + ".yml"));
        Object setting = sources.getFirst().getProperty("spring.flyway.out-of-order");
        assertEquals(Boolean.TRUE, setting, "Actual checked-in profile must enable out-of-order");
        Flyway upgrade = Flyway.configure().dataSource(url, "root", "review-only-password")
                .locations("filesystem:src/main/resources/db/migration").outOfOrder((Boolean) setting).load();
        assertEquals(1, upgrade.migrate().migrationsExecuted);
        upgrade.validate();
        assertEquals(8, upgrade.info().applied().length);
        assertEquals(0, upgrade.migrate().migrationsExecuted, "Second migrate must be a no-op");
        try (var connection = DriverManager.getConnection(url, "root", "review-only-password");
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT marker FROM review_existing_data WHERE id = 1")) {
                assertTrue(rows.next());
                assertEquals("preserve-me", rows.getString(1));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM finance_sync")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = 1")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
        }
        System.out.println("REVIEW " + profile + ": V13->V4 upgrade, validation, repeat migrate and existing data preservation passed");
    }

    @Test void disabledOutOfOrderStillReproducesOriginalFailure() throws Exception {
        String url = prepareV13("review_fixed_control");
        Flyway upgrade = Flyway.configure().dataSource(url, "root", "review-only-password")
                .locations("filesystem:src/main/resources/db/migration").outOfOrder(false).load();
        var failure = assertThrows(FlywayValidateException.class, upgrade::migrate);
        assertTrue(failure.getMessage().contains("Detected resolved migration not applied to database: 4"));
    }
}
