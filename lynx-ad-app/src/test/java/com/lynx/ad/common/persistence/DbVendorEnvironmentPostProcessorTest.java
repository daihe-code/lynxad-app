package com.lynx.ad.common.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

class DbVendorEnvironmentPostProcessorTest {

    @Test
    void devUsesTheVendorSwitch() {
        ConfigurableEnvironment mysql = environment("dev", "mysql");
        DbVendorEnvironmentPostProcessor.apply(mysql);
        assertTrue(mysql.getProperty("spring.datasource.url").startsWith("jdbc:mysql:"));

        ConfigurableEnvironment postgresql = environment("dev", "postgresql");
        DbVendorEnvironmentPostProcessor.apply(postgresql);
        assertTrue(postgresql.getProperty("spring.datasource.url").startsWith("jdbc:postgresql:"));
    }

    @Test
    void testProfileKeepsTheInjectedUrl() {
        ConfigurableEnvironment environment = environment("test", "postgresql");
        environment.getPropertySources().addFirst(new MapPropertySource(
                "container", Map.of("spring.datasource.url", "jdbc:postgresql://container/lynx")));
        DbVendorEnvironmentPostProcessor.apply(environment);
        assertEquals("jdbc:postgresql://container/lynx", environment.getProperty("spring.datasource.url"));
        assertNull(environment.getPropertySources().get(DbVendorEnvironmentPostProcessor.SOURCE));
    }

    @Test
    void packagedDevConfigDefaultsToMysql() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(context -> DbVendorEnvironmentPostProcessor.apply(context.getEnvironment()))
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertEquals("mysql", context.getEnvironment().getProperty("lynx.ad.db.vendor"));
                    assertTrue(context.getEnvironment().getProperty("spring.datasource.url").startsWith("jdbc:mysql:"));
                });
    }

    @Test
    void dailyStartLoadsDefaultMysqlThroughSpringFactories() {
        assertTrue(SpringFactoriesLoader.loadFactoryNames(
                        EnvironmentPostProcessor.class, DbVendorEnvironmentPostProcessor.class.getClassLoader())
                .contains(DbVendorEnvironmentPostProcessor.class.getName()));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("SPRING_PROFILES_ACTIVE", "dev");
        properties.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://example/lynx-ad-app");
        properties.put("SPRING_DATASOURCE_USERNAME", "generic-user");
        properties.put("SPRING_DATASOURCE_PASSWORD", "generic-secret");
        try (ConfigurableApplicationContext context = boot(properties)) {
            String url = context.getEnvironment().getProperty("spring.datasource.url");
            assertNotNull(url);
            assertTrue(url.startsWith("jdbc:mysql:"));
            assertFalse(url.contains("generic-secret"));
        }
    }

    @Test
    void defaultMysqlQuotesColumns() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("SPRING_PROFILES_ACTIVE", "dev");
        properties.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://example/lynx-ad-app");
        properties.put("SPRING_DATASOURCE_USERNAME", "generic-user");
        properties.put("SPRING_DATASOURCE_PASSWORD", "generic-secret");
        try (ConfigurableApplicationContext context = boot(properties)) {
            PropertySource<?> source =
                    context.getEnvironment().getPropertySources().get(DbVendorEnvironmentPostProcessor.SOURCE);
            assertNotNull(source);
            assertEquals("`%s`", source.getProperty("mybatis-plus.global-config.db-config.column-format"));
        }
    }

    @Test
    void trialUsesTheExistingConnectionKeys() {
        Map<String, Object> properties = cloudPlaceholders("trial");
        properties.put("SPRING_DATASOURCE_URL", "jdbc:mysql://example/lynx-ad-app");
        properties.put("SPRING_DATASOURCE_USERNAME", "generic-user");
        properties.put("SPRING_DATASOURCE_PASSWORD", "generic-secret");
        try (ConfigurableApplicationContext context = boot(properties)) {
            assertCopied(context.getEnvironment(), "jdbc:mysql://example/lynx-ad-app", "generic-user", "generic-secret");
        }
    }

    @Test
    void productionUsesTheSameConnectionKeys() {
        Map<String, Object> properties = cloudPlaceholders("prod");
        properties.put("LYNX_DB_VENDOR", "postgresql");
        properties.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://example/lynx-ad-app");
        properties.put("SPRING_DATASOURCE_USERNAME", "generic-user");
        properties.put("SPRING_DATASOURCE_PASSWORD", "generic-secret");
        try (ConfigurableApplicationContext context = boot(properties)) {
            assertCopied(context.getEnvironment(), "jdbc:postgresql://example/lynx-ad-app", "generic-user", "generic-secret");
            assertEquals("false", context.getEnvironment().getProperty("lynx.ad.demo-mode"));
        }
    }

    @Test
    void engineUrlOverridesTheGenericUrlPerField() {
        Map<String, Object> properties = cloudPlaceholders("trial");
        properties.put("LYNX_DB_VENDOR", "mysql");
        properties.put("LYNX_DB_MYSQL_URL", "jdbc:mysql://engine.example/lynx-ad-app");
        properties.put("SPRING_DATASOURCE_URL", "jdbc:mysql://generic.example/lynx-ad-app");
        properties.put("SPRING_DATASOURCE_USERNAME", "generic-user");
        properties.put("SPRING_DATASOURCE_PASSWORD", "generic-secret");
        try (ConfigurableApplicationContext context = boot(properties)) {
            assertCopied(
                    context.getEnvironment(),
                    "jdbc:mysql://engine.example/lynx-ad-app",
                    "generic-user",
                    "generic-secret");
        }
    }

    @Test
    void alternateStoreDoesNotQuoteColumns() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("SPRING_PROFILES_ACTIVE", "dev");
        properties.put("LYNX_DB_VENDOR", "postgresql");
        try (ConfigurableApplicationContext context = boot(properties)) {
            PropertySource<?> source =
                    context.getEnvironment().getPropertySources().get(DbVendorEnvironmentPostProcessor.SOURCE);
            assertNotNull(source);
            assertNull(source.getProperty("mybatis-plus.global-config.db-config.column-format"));
        }
    }

    @Test
    void devSwitchesToTheLocalAlternateStore() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("SPRING_PROFILES_ACTIVE", "dev");
        properties.put("LYNX_DB_VENDOR", "postgresql");
        try (ConfigurableApplicationContext context = boot(properties)) {
            String url = context.getEnvironment().getProperty("spring.datasource.url");
            assertEquals("jdbc:postgresql://localhost:5432/lynx-ad-app", url);
            assertFalse(url.startsWith("jdbc:mysql:"));
        }
    }

    @Test
    void mismatchedSchemeFailsBeforeMigration() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DbVendorFlywayConfig.assertUrlMatches("mysql", "jdbc:postgresql://example/lynx-ad-app"));
        assertTrue(failure.getMessage().contains("does not match"));
        assertFalse(failure.getMessage().contains("jdbc:postgresql://example/lynx-ad-app"));
    }

    @Test
    void blankPasswordNamesTheMissingKey() {
        ConfigurableEnvironment environment = environment("dev", "mysql");
        environment.getPropertySources().addFirst(new MapPropertySource(
                "blank-password",
                Map.of(
                        "lynx.ad.db.mysql.url", "jdbc:mysql://example/lynx-ad-app",
                        "lynx.ad.db.mysql.username", "mysql-user",
                        "lynx.ad.db.mysql.password", " ")));
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> DbVendorEnvironmentPostProcessor.apply(environment));
        assertTrue(failure.getMessage().contains("lynx.ad.db.mysql.password"));
        assertFalse(failure.getMessage().contains("jdbc:mysql://example/lynx-ad-app"));
        assertNull(environment.getPropertySources().get(DbVendorEnvironmentPostProcessor.SOURCE));
    }

    @Test
    void unknownVendorFailsBeforeCopy() {
        ConfigurableEnvironment environment = environment("dev", "oracle");
        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> DbVendorEnvironmentPostProcessor.apply(environment));
        assertTrue(failure.getMessage().contains("mysql or postgresql"));
        assertNull(environment.getPropertySources().get(DbVendorEnvironmentPostProcessor.SOURCE));
    }

    private static ConfigurableEnvironment environment(String profile, String vendor) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("lynx.ad.db.vendor", vendor);
        values.put("lynx.ad.db.mysql.url", "jdbc:mysql://127.0.0.1:3306/lynx-ad-app");
        values.put("lynx.ad.db.mysql.username", "mysql-user");
        values.put("lynx.ad.db.mysql.password", "mysql-secret");
        values.put("lynx.ad.db.postgresql.url", "jdbc:postgresql://localhost:5432/lynx-ad-app");
        values.put("lynx.ad.db.postgresql.username", "pg-user");
        values.put("lynx.ad.db.postgresql.password", "pg-secret");
        environment.getPropertySources().addLast(new MapPropertySource("fixtures", values));
        return environment;
    }

    private static Map<String, Object> cloudPlaceholders(String profile) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("SPRING_PROFILES_ACTIVE", profile);
        properties.put("SPRING_DATA_REDIS_HOST", "127.0.0.1");
        properties.put("SPRING_DATA_REDIS_PORT", "6379");
        properties.put("SPRING_DATA_REDIS_PASSWORD", "redis-secret");
        properties.put("LYNX_STORAGE_LOCAL_DIR", "/tmp/lynx-ad-test");
        properties.put("LYNX_JWT_SECRET", "jwt-secret-for-test-only-32b!!");
        properties.put("LYNX_AES_KEY", "aes-key-for-test-only-32bytes!!");
        properties.put("LYNX_OAUTH_WEB_CALLBACK_URI", "http://localhost/callback");
        properties.put("QIANCHUAN_REDIRECT_URI", "http://localhost/qc");
        properties.put("QIANCHUAN_APPID", "app-id");
        properties.put("QIANCHUAN_SECRET", "app-secret");
        return properties;
    }

    private static void assertCopied(ConfigurableEnvironment environment, String url, String username, String password) {
        PropertySource<?> source = environment.getPropertySources().get(DbVendorEnvironmentPostProcessor.SOURCE);
        assertNotNull(source);
        assertEquals(url, source.getProperty("spring.datasource.url"));
        assertEquals(username, source.getProperty("spring.datasource.username"));
        assertEquals(password, source.getProperty("spring.datasource.password"));
    }

    private static ConfigurableApplicationContext boot(Map<String, Object> properties) {
        Map<String, Object> values = new LinkedHashMap<>(properties);
        values.putIfAbsent(
                "spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration");
        SpringApplication application = new SpringApplication(DatasourceSwitchProbe.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(values);
        application.setRegisterShutdownHook(false);
        return application.run();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "com.lynx.ad.datasource.switchprobe")
    static class DatasourceSwitchProbe {}
}
