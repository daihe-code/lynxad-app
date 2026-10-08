package com.lynx.ad.modules.creative.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CreativeJsonMockContentTest {
    @Test
    void provenanceSurvivesPersistenceAndMissingFlagPreservesLegacyRealMaterial() {
        var json = new CreativeJson(new ObjectMapper());
        var old = json.readArtifacts("[{\"productId\":\"p\",\"titles\":[],\"videoId\":\"real-video\"}]").getFirst();
        assertFalse(old.mockContent());
        var flagged = old.withMockContent(true);
        assertTrue(json.readArtifacts(json.artifacts(java.util.List.of(flagged))).getFirst().mockContent());
        assertTrue(flagged.withStorage("key", null, "local", "p.mp4", 24L, "VIDEO_VERTICAL", null).mockContent());
    }
}
