package com.lynx.ad.application.creative;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.http.OceanJson;
import com.lynx.ad.common.http.QianchuanRestClient;
import com.lynx.ad.modules.creative.domain.model.MaterialType;
import com.lynx.ad.modules.creative.domain.port.MaterialLibraryPort;
import com.lynx.ad.modules.creative.infrastructure.adapter.LiveMaterialLibraryPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LiveMaterialLibraryTimeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final QianchuanRestClient client = mock(QianchuanRestClient.class);
    private final LiveMaterialLibraryPort port = new LiveMaterialLibraryPort(client, new LynxAdProperties(), new OceanJson(json));
    private final Instant original = Instant.parse("2026-10-19T12:31:36Z");

    private Instant created(String value, MaterialType type) throws Exception {
        var row = json.createObjectNode().put("id", "video-original").put("material_id", "123");
        if (value != null) row.set("create_time", json.readTree(value));
        var data = json.createObjectNode(); data.putArray("list").add(row);
        when(client.exchange(any(), eq(String.class))).thenReturn(json.createObjectNode().put("code", 0).set("data", data).toString());
        return port.list(new MaterialLibraryPort.MaterialLibraryQuery("account", type, null, null, 1, 20, "fixture"))
                .items().getFirst().createdAt();
    }

    @Test void parsesActualPlatformLocalTimeForAllMaterialTypes() throws Exception {
        for (var type : MaterialType.values()) {
            assertEquals(original, created("\"2026-10-19 20:31:36\"", type));
            assertEquals(original, created("\"2026-10-19T20:31:36\"", type));
            assertEquals(original.plusMillis(123), created("\"2026-10-19 20:31:36.123\"", type));
        }
    }

    @Test void preservesSecondsMillisecondsAndExplicitOffsets() throws Exception {
        for (String value : java.util.List.of("\"2026-10-19T12:31:36Z\"", "\"2026-10-19T20:31:36+08:00\"",
                Long.toString(original.getEpochSecond()), "\"" + original.getEpochSecond() + "\"",
                Long.toString(original.toEpochMilli()), "\"" + original.toEpochMilli() + "\"")) {
            assertEquals(original, created(value, MaterialType.VIDEO));
        }
    }

    @Test void unknownOrInvalidCreationTimesNeverBecomeRefreshTime() throws Exception {
        assertNull(created(null, MaterialType.VIDEO));
        for (String value : java.util.List.of("null", "\"\"", "\"invalid\"", "0", "true", "{}", "[]",
                "\"2026-02-30 12:00:00\"", "\"20261019203136\"", "9999999999999999999")) {
            assertNull(created(value, MaterialType.IMAGE), value);
        }
    }
}
