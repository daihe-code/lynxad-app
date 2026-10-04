package com.lynx.ad.common.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Copies the connection selected by {@code lynx.ad.db.vendor} into {@code spring.datasource.*}
 * for the {@code dev}, {@code trial}, and {@code prod} profiles. Trial and production use the
 * same copy as local development. When the vendor is {@code mysql}, the same property source
 * sets the MyBatis-Plus column format so reserved identifiers are quoted. PostgreSQL leaves
 * that key unset. The {@code test} profile keeps the datasource injected by the test.
 */
public class DbVendorEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE = "lynxDbVendor";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        apply(environment);
    }

    static void apply(ConfigurableEnvironment environment) {
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return;
        }
        boolean selectedProfile = environment.acceptsProfiles(Profiles.of("dev"))
                || environment.acceptsProfiles(Profiles.of("trial"))
                || environment.acceptsProfiles(Profiles.of("prod"));
        if (!selectedProfile) {
            return;
        }
        String vendor = DbVendorFlywayConfig.requireVendor(environment.getProperty("lynx.ad.db.vendor", "mysql"));
        String prefix = "lynx.ad.db." + vendor + ".";
        Map<String, Object> selected = new LinkedHashMap<>();
        selected.put("spring.datasource.url", required(environment, prefix + "url", true));
        selected.put("spring.datasource.username", required(environment, prefix + "username", false));
        selected.put("spring.datasource.password", required(environment, prefix + "password", false));
        if ("mysql".equals(vendor)) {
            selected.put("mybatis-plus.global-config.db-config.column-format", "`%s`");
        }
        environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, selected));
    }

    private static String required(ConfigurableEnvironment environment, String key, boolean url) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            if (url) {
                throw new IllegalStateException("Missing " + key + " or SPRING_DATASOURCE_URL");
            }
            throw new IllegalStateException("Missing " + key);
        }
        return value;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
