package com.ssafy.dodam.domain.finance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FinanceMigrationUpgradeReviewTest {
    @TempDir Path initialMigrations;

    private String prepareV13(String database) throws Exception {
        String rootUrl = "jdbc:mysql://127.0.0.1:33364/?allowPublicKeyRetrieval=true&useSSL=false";
        try (var connection = DriverManager.getConnection(rootUrl, "root", "review-only-password");
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
        return url;
    }

    @Test void existingV13DatabaseMustAcceptNewV4WithCurrentDefaults() throws Exception {
        String url = prepareV13("review_default");
        Flyway upgrade = Flyway.configure().dataSource(url, "root", "review-only-password")
                .locations("filesystem:src/main/resources/db/migration").load();
        assertDoesNotThrow(upgrade::migrate, "Existing V13 database must upgrade without startup failure");
        assertTrue(java.util.Arrays.stream(upgrade.info().applied())
                .anyMatch(m -> m.getVersion().toString().equals("4")));
    }

    @Test void explicitOutOfOrderAllowsV4UpgradeInDisposableDatabase() throws Exception {
        String url = prepareV13("review_out_of_order");
        Flyway upgrade = Flyway.configure().dataSource(url, "root", "review-only-password")
                .locations("filesystem:src/main/resources/db/migration").outOfOrder(true).load();
        assertDoesNotThrow(upgrade::migrate);
        assertTrue(java.util.Arrays.stream(upgrade.info().applied())
                .anyMatch(m -> m.getVersion().toString().equals("4")));
    }
}
