package com.lynx.ad.modules.creative.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.lynx.ad.modules.creative.domain.service.GeneratedAssetName;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class GeneratedImageNamesMigrationTest {
    private static final Instant CREATED = Instant.parse("2026-10-30T04:00:00Z");
    private static String id(int value) { return "6013b26f-72b1-4f64-9119-" + "%012d".formatted(value); }

    @ParameterizedTest @ValueSource(strings = {"PostgreSQL", "MySQL"})
    void legacyImagesGetStableUniqueNamesWithoutChangingFilesOrBindings(String mode) throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:image_names_" + UUID.randomUUID()
                + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        String directory = "db/migration" + (mode.equals("MySQL") ? "-mysql" : "") + "/";
        try (var connection = ds.getConnection()) {
            for (String script : List.of("V12__creative_asset_upload.sql", "V19__engine_asset_reference.sql"))
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(directory + script));
        }
        var repository = new AssetUploadRepository(jdbc);
        List<AssetUploadRepository.Asset> assets = new ArrayList<>();
        assets.add(asset(1, "generated.jpg", "IMAGE", "engine_oss", true));
        assets.add(asset(2, "generated.jpg", "IMAGE", "engine_oss", true));
        assets.add(asset(3, "generated.png", "IMAGE", "engine_oss", true));
        assets.add(asset(4, "generated.jpeg", "IMAGE", "engine_oss", true));
        assets.add(asset(5, "GENERATED.PNG", "IMAGE", "engine_oss", true));
        assets.add(asset(6, "generated.jpg", "IMAGE", "oss", false));
        assets.add(asset(7, "generated.jpg", "IMAGE", "oss", true));
        assets.add(asset(8, GeneratedAssetName.create("瑜伽服商品卡", "IMAGE_0", CREATED, id(8), ".jpg"), "IMAGE", "engine_oss", true));
        assets.add(asset(9, "generated.mp4", "VIDEO", "engine_oss", true));
        for (var asset : assets) {
            if (asset.storageMode().equals("engine_oss"))
                repository.registerEngine(asset, new AssetUploadRepository.EngineReference(asset.id(), asset.tenantId(),
                        "job", "material-" + asset.id(), asset.kind().equals("VIDEO") ? "VIDEO" : "IMAGE_0",
                        "https://source.example/" + asset.objectKey()));
            else repository.insert(asset);
        }
        jdbc.update("INSERT INTO creative_asset_platform(id,tenant_id,asset_id,account_id,status,object_id,material_id,next_attempt_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                "sync", "tenant-a", id(1), "account", "UPLOADED", "qc-image", "qc-material",
                Timestamp.from(CREATED), Timestamp.from(CREATED), Timestamp.from(CREATED));
        var before = jdbc.queryForList("SELECT * FROM creative_asset ORDER BY id");
        var engineBefore = jdbc.queryForList("SELECT * FROM creative_engine_asset ORDER BY asset_id");
        var platformBefore = jdbc.queryForList("SELECT * FROM creative_asset_platform ORDER BY id");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(directory + "V48__unique_legacy_generated_image_names.sql"));
        }
        var names = new HashSet<String>();
        for (int index = 1; index <= 5; index++) {
            var renamed = repository.require("tenant-a", id(index));
            assertTrue(names.add(renamed.filename()));
            assertTrue(renamed.filename().contains(renamed.id()));
            assertEquals(index == 3 || index == 5 ? ".png" : index == 4 ? ".jpeg" : ".jpg",
                    renamed.filename().substring(renamed.filename().lastIndexOf('.')));
        }
        for (int index = 6; index <= 9; index++)
            assertEquals(assets.get(index - 1).filename(), repository.require("tenant-a", id(index)).filename());
        var after = jdbc.queryForList("SELECT * FROM creative_asset ORDER BY id");
        for (int index = 0; index < before.size(); index++) {
            String filenameKey = before.get(index).keySet().stream().filter(key -> key.equalsIgnoreCase("filename")).findFirst().orElseThrow();
            var oldRow = new LinkedHashMap<>(before.get(index));
            var newRow = new LinkedHashMap<>(after.get(index));
            oldRow.remove(filenameKey); newRow.remove(filenameKey);
            assertEquals(oldRow, newRow);
        }
        assertEquals(engineBefore, jdbc.queryForList("SELECT * FROM creative_engine_asset ORDER BY asset_id"));
        assertEquals(platformBefore, jdbc.queryForList("SELECT * FROM creative_asset_platform ORDER BY id"));
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(directory + "V48__unique_legacy_generated_image_names.sql"));
        }
        assertEquals(after, jdbc.queryForList("SELECT * FROM creative_asset ORDER BY id"));
        jdbc.execute("SHUTDOWN");
    }

    private static AssetUploadRepository.Asset asset(int index, String filename, String kind, String mode, boolean aigc) {
        return new AssetUploadRepository.Asset(id(index), "tenant-a", "user", id(index), kind, filename,
                kind.equals("VIDEO") ? "video/mp4" : filename.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg",
                1234, "objects/" + id(index) + "/original", "STORED", mode, null, null, "md5", aigc,
                1200, 1200, null, null, null, CREATED, CREATED);
    }
}
