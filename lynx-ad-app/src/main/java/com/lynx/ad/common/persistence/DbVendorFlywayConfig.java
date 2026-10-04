package com.lynx.ad.common.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DbVendorFlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer lynxFlywayLocations(
            @Value("${lynx.ad.db.vendor:mysql}") String vendor) {
        String location = "mysql".equals(requireVendor(vendor))
                ? "classpath:db/migration-mysql"
                : "classpath:db/migration";
        return configuration -> configuration.locations(location);
    }

    @Bean
    public FlywayMigrationStrategy lynxFlywayMigrationStrategy(
            DataSource dataSource, @Value("${lynx.ad.db.vendor:mysql}") String vendor) {
        return flyway -> {
            prepare(dataSource, vendor);
            flyway.migrate();
        };
    }

    public static String requireVendor(String raw) {
        if (raw == null) {
            throw new IllegalStateException("lynx.ad.db.vendor must be mysql or postgresql");
        }
        String vendor = raw.trim().toLowerCase(Locale.ROOT);
        if (!"mysql".equals(vendor) && !"postgresql".equals(vendor)) {
            throw new IllegalStateException("lynx.ad.db.vendor must be mysql or postgresql");
        }
        return vendor;
    }

    /**
     * Checks vendor, JDBC scheme, and whether the target database is empty or already managed.
     * Throws before the caller may migrate.
     */
    public static void prepare(DataSource dataSource, String vendor) {
        String normalized = requireVendor(vendor);
        try (Connection connection = dataSource.getConnection()) {
            assertUrlMatches(normalized, connection.getMetaData().getURL());
            assertEmptyOrManaged(connection);
        } catch (SQLException ex) {
            throw new IllegalStateException("Refusing to migrate", ex);
        }
    }

    public static void assertUrlMatches(String vendor, String url) {
        String expected = "mysql".equals(vendor) ? "jdbc:mysql:" : "jdbc:postgresql:";
        if (url == null || !url.regionMatches(true, 0, expected, 0, expected.length())) {
            throw new IllegalStateException(
                    "Refusing to migrate: vendor " + vendor + " does not match JDBC URL scheme");
        }
    }

    public static void assertEmptyOrManaged(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        boolean mysql = product != null && product.contains("MySQL");
        String sql = mysql
                ? "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()"
                : "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()";
        boolean history = false;
        boolean other = false;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String name = rows.getString(1);
                if (name != null && name.equalsIgnoreCase("flyway_schema_history")) {
                    history = true;
                } else {
                    other = true;
                }
            }
        }
        if (!history && other) {
            throw new IllegalStateException(
                    "Refusing to migrate: database has tables but no flyway_schema_history");
        }
    }
}
