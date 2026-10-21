package com.lynx.ad.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lynx.ad.common.persistence.DbVendorFlywayConfig;
import com.lynx.ad.common.persistence.JsonbStringTypeHandler;
import com.lynx.ad.modules.dataops.infrastructure.persistence.MetricSnapshotMapper;
import com.lynx.ad.modules.dataops.infrastructure.persistence.MetricSnapshotPo;
import com.lynx.ad.starter.LynxAdApplication;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = LynxAdApplication.class)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MysqlSchemaSmokeIT {

    @SuppressWarnings("resource")
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("lynx_ad_app")
            .withUsername("lynx")
            .withPassword("lynx_dev")
            .withCommand(
                    "mysqld",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_as_cs");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MetricSnapshotMapper metricSnapshotMapper;
    @Autowired
    private com.lynx.ad.modules.creative.domain.port.CreativeTaskRepository creativePackages;

    @Test
    void creativePackageConfirmationRoundTrips() {
        CreativePackagePersistenceAssertions.verifyConfirmationRoundTrip(jdbc(),creativePackages);
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MysqlSchemaSmokeIT::mysqlUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("lynx.ad.db.vendor", () -> "mysql");
        registry.add("mybatis-plus.global-config.db-config.column-format", () -> "`%s`");
    }

    @Test
    void migratedSchemaRoundTrips() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("41", latestVersion(connection));
            assertTrue(businessTableCount(connection) >= 24);

            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE billing_plan SET benefits_json = ? WHERE code = 'BASIC'")) {
                new JsonbStringTypeHandler().setNonNullParameter(update, 1, "[\"roundtrip\"]", null);
                assertEquals(1, update.executeUpdate());
            }
            assertEquals("[\"roundtrip\"]", normalizeJson(scalar(connection,
                    "SELECT benefits_json FROM billing_plan WHERE code = 'BASIC'")));

            String planId = UUID.randomUUID().toString();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ad_plan (id, tenant_id, aweme_id, name, status, audience_json) VALUES (?,?,?,?,?,?)")) {
                insert.setString(1, planId);
                insert.setString(2, "tenant-audience");
                insert.setString(3, "aweme-audience");
                insert.setString(4, "audience-plan");
                insert.setString(5, "draft");
                insert.setString(6, "{\"city\":\"hz\"}");
                insert.executeUpdate();
            }
            try (PreparedStatement read = connection.prepareStatement(
                    "SELECT audience_json FROM ad_plan WHERE id = ?")) {
                read.setString(1, planId);
                try (ResultSet rows = read.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("{\"city\":\"hz\"}", normalizeJson(rows.getString(1)));
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO plan_metric_snapshot (tenant_id, snapshot_ts) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, "tenant-metric");
                insert.setTimestamp(2, Timestamp.from(Instant.parse("2026-10-04T00:00:00Z")));
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    assertTrue(keys.getLong(1) > 0);
                }
            }

            Instant written = Instant.parse("2026-10-04T08:00:00.123Z");
            String instantPlan = UUID.randomUUID().toString();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO ad_plan (id, tenant_id, aweme_id, name, status, created_at) VALUES (?,?,?,?,?,?)")) {
                insert.setString(1, instantPlan);
                insert.setString(2, "tenant-instant");
                insert.setString(3, "aweme-instant");
                insert.setString(4, "instant-plan");
                insert.setString(5, "draft");
                insert.setTimestamp(6, Timestamp.from(written));
                insert.executeUpdate();
            }
            try (PreparedStatement read = connection.prepareStatement("SELECT created_at FROM ad_plan WHERE id = ?")) {
                read.setString(1, instantPlan);
                try (ResultSet rows = read.executeQuery()) {
                    assertTrue(rows.next());
                    long delta = Math.abs(rows.getTimestamp(1).toInstant().toEpochMilli() - written.toEpochMilli());
                    assertTrue(delta <= 1, "instant delta " + delta);
                }
            }

            assertUniqueName(connection);
            assertPriceScale(connection);
        }
    }

    @Test
    void refusesMismatchedVendorBeforeMigrate() throws Exception {
        DataSource postgresUrl = dataSourceReturning("jdbc:postgresql://127.0.0.1/lynx_ad_app");
        IllegalStateException mysqlVendor = assertThrows(IllegalStateException.class,
                () -> DbVendorFlywayConfig.prepare(postgresUrl, "mysql"));
        assertTrue(mysqlVendor.getMessage().contains("does not match"));

        DataSource mysqlUrl = dataSourceReturning(mysqlUrl());
        IllegalStateException postgresVendor = assertThrows(IllegalStateException.class,
                () -> DbVendorFlywayConfig.prepare(mysqlUrl, "postgresql"));
        assertTrue(postgresVendor.getMessage().contains("does not match"));

        try (Connection connection = dataSource.getConnection()) {
            assertEquals("41", latestVersion(connection));
        }
        assertThrows(IllegalStateException.class, () -> DbVendorFlywayConfig.prepare(dataSource, "postgresql"));
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("41", latestVersion(connection));
        }
    }

    @Test
    void refusesDirtyDatabaseWithoutDroppingTables() throws Exception {
        String adminUrl = mysqlUrl().replace("/lynx_ad_app", "/mysql");
        try (Connection admin = DriverManager.getConnection(adminUrl, "root", MYSQL.getPassword());
                Statement statement = admin.createStatement()) {
            statement.execute(
                    "CREATE DATABASE IF NOT EXISTS dirty_check CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs");
        }
        String dirtyUrl = mysqlUrl().replace("/lynx_ad_app", "/dirty_check");
        try (Connection dirty = DriverManager.getConnection(dirtyUrl, "root", MYSQL.getPassword());
                Statement statement = dirty.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS stray");
            statement.execute("CREATE TABLE stray (id INT)");
        }
        DriverManagerDataSource dirtySource = new DriverManagerDataSource(dirtyUrl, "root", MYSQL.getPassword());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> DbVendorFlywayConfig.prepare(dirtySource, "mysql"));
        assertTrue(failure.getMessage().contains("flyway_schema_history"));
        try (Connection dirty = DriverManager.getConnection(dirtyUrl, "root", MYSQL.getPassword())) {
            assertEquals(1, countNamedTable(dirty, "stray"));
            assertEquals(0, countNamedTable(dirty, "flyway_schema_history"));
        }
    }

    private static void assertUniqueName(Connection connection) throws SQLException {
        insertPlan(connection, "tenant-name", "aweme-name", "same-name", "draft");
        SQLException duplicate = assertThrows(SQLException.class,
                () -> insertPlan(connection, "tenant-name", "aweme-name", "same-name", "paused"));
        assertEquals(1062, duplicate.getErrorCode());
        insertPlan(connection, "tenant-name", "aweme-name", "finished-name", "finished");
        insertPlan(connection, "tenant-name", "aweme-name", "finished-name", "draft");
    }

    @Test
    void quotedConvertRoundTrips() {
        Instant ts = Instant.parse("2026-10-04T01:00:00Z");
        MetricSnapshotPo twelve = snapshot("tenant-convert-12", ts, 12L);
        metricSnapshotMapper.insert(twelve);
        assertEquals(12L, metricSnapshotMapper.selectById(twelve.getId()).getConvert());

        MetricSnapshotPo zero = snapshot("tenant-convert-0", ts.plusSeconds(1), 0L);
        metricSnapshotMapper.insert(zero);
        assertEquals(0L, metricSnapshotMapper.selectById(zero.getId()).getConvert());
    }

    private static MetricSnapshotPo snapshot(String tenantId, Instant snapshotTs, Long convert) {
        MetricSnapshotPo po = new MetricSnapshotPo();
        po.setTenantId(tenantId);
        po.setSnapshotTs(snapshotTs);
        po.setConvert(convert);
        return po;
    }

    private static void assertPriceScale(Connection connection) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO product (id, tenant_id, aweme_id, qc_product_id, price) VALUES (?,?,?,?,?)")) {
            insert.setString(1, id);
            insert.setString(2, "tenant-price");
            insert.setString(3, "aweme-price");
            insert.setString(4, "qc-price");
            insert.setBigDecimal(5, new BigDecimal("12.34"));
            insert.executeUpdate();
        }
        try (PreparedStatement read = connection.prepareStatement("SELECT price FROM product WHERE id = ?")) {
            read.setString(1, id);
            try (ResultSet rows = read.executeQuery()) {
                assertTrue(rows.next());
                BigDecimal price = rows.getBigDecimal(1);
                assertEquals(0, price.compareTo(new BigDecimal("12.34")));
                assertEquals(2, price.scale());
            }
        }
    }

    private static void insertPlan(Connection connection, String tenant, String aweme, String name, String status)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO ad_plan (id, tenant_id, aweme_id, name, status) VALUES (?,?,?,?,?)")) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, tenant);
            insert.setString(3, aweme);
            insert.setString(4, name);
            insert.setString(5, status);
            insert.executeUpdate();
        }
    }

    private static DataSource dataSourceReturning(String url) throws SQLException {
        Connection connection = mock(Connection.class);
        java.sql.DatabaseMetaData meta = mock(java.sql.DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn(url);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    private static String mysqlUrl() {
        String base = MYSQL.getJdbcUrl();
        String params = "characterEncoding=utf8&connectionCollation=utf8mb4_0900_as_cs"
                + "&serverTimezone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
        return base + (base.contains("?") ? "&" : "?") + params;
    }

    private static int countNamedTable(Connection connection, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'dirty_check' AND table_name = ?")) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static String latestVersion(Connection connection) throws SQLException {
        return scalar(connection,
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1");
    }

    private static int businessTableCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private static String normalizeJson(String raw) {
        return raw == null ? null : raw.replace(" ", "");
    }
}
