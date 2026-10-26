package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;

import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort.PlatformMaterial;
import com.lynx.ad.modules.creative.infrastructure.persistence.PlatformMaterialCatalog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class PlatformMaterialTimeCatalogTest {
    @Test void syncReplacesPreviouslyFabricatedDateAndRoundTripsUnknownTimeWithoutEpochFallback() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V33__platform_material_catalog.sql"),
                new ClassPathResource("db/migration/V45__platform_material_original_time.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE oauth_account(id VARCHAR(36),tenant_id VARCHAR(36),platform VARCHAR(32),status VARCHAR(32))");
        jdbc.update("INSERT INTO oauth_account VALUES('account','tenant','QIANCHUAN','ACTIVE')");
        var catalog = new PlatformMaterialCatalog(jdbc);
        catalog.replace("tenant", "account", "advertiser", MaterialType.VIDEO,
                List.of(material("old", Instant.parse("2026-10-26T06:00:00Z"))));
        var original = Instant.parse("2026-10-19T12:31:36Z");
        catalog.replace("tenant", "account", "advertiser", MaterialType.VIDEO, List.of(material("old", original), material("unknown", null)));
        var rows = catalog.list("tenant", Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"));
        assertEquals(2, rows.size());
        assertEquals(original, rows.stream().filter(row -> row.objectId().equals("old")).findFirst().orElseThrow().createdAt());
        assertNull(catalog.find("tenant", "account", MaterialType.VIDEO, "m:unknown").orElseThrow().createdAt());
        assertEquals(2, catalog.list("tenant").size());
        assertEquals(List.of("unknown"), catalog.list("tenant", Instant.parse("2026-10-26T00:00:00Z"),
                Instant.parse("2026-10-27T00:00:00Z")).stream().map(PlatformMaterialCatalog.Material::objectId).toList());
    }

    private PlatformMaterial material(String id, Instant created) {
        return new PlatformMaterial(id, id, id + ".mp4", 1024, 720, 1280, 10, "VIDEO_VERTICAL", "EXTERNAL", created, "", "", false, "NONE");
    }
}
