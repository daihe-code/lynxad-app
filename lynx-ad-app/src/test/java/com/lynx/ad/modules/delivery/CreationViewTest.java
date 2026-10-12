package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import org.junit.jupiter.api.Test;

class CreationViewTest {
    @Test void pricesPersistWithExactIdsAndOlderSnapshotsRemainReadable() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();
        var old=json.readValue("{\"productId\":\"3841023363610968621\",\"qcProductId\":\"3841023363610968621\",\"name\":\"手套\",\"tags\":[],\"unavailableReasons\":[]}",CreationState.Product.class);
        assertNull(old.priceInfo());
        var quote=new com.lynx.ad.modules.catalog.domain.model.PlatformProductPrice(new java.math.BigDecimal("17.89"),new java.math.BigDecimal("19.89"),null,"QIANCHUAN_AVAILABLE",java.time.Instant.parse("2026-10-11T14:00:00Z"),false);
        var product=new CreationState.Product(old.productId(),old.qcProductId(),old.name(),quote.minPrice(),null,null,null,null,java.util.List.of(),java.util.List.of(),quote,null);
        var restored=json.readValue(json.writeValueAsString(product),CreationState.Product.class);
        assertEquals(product,restored);assertEquals("3841023363610968621",restored.qcProductId());
    }
    @Test void browserReceivesPreviewButNotWorkerStateOrSourceDownloadUrls() throws Exception {
        var s = new CreationState();
        s.planId = "plan"; s.tenantId = "tenant"; s.userId = "owner"; s.engineJobId = "internal-job";
        var m = new CreationState.Material(); m.id = "material"; m.previewUrl = "https://platform.test/preview";
        m.text = "图文正文";
        m.sourceUrls.put("VIDEO", "https://engine.test/private-download?signature=private");
        m.importedAssets.put("VIDEO", "internal-asset"); s.materials.add(m);
        var wire = new ObjectMapper().valueToTree(CreationView.of(s));
        assertEquals("https://platform.test/preview", wire.path("materials").get(0).path("previewUrl").asText());
        assertEquals("图文正文", wire.path("materials").get(0).path("text").asText());
        assertFalse(wire.has("tenantId")); assertFalse(wire.has("engineJobId")); assertFalse(wire.has("feedback"));
        assertFalse(wire.toString().contains("private-download")); assertFalse(wire.toString().contains("internal-asset"));
        // Projecting the API response must not destroy the persisted worker inputs.
        assertEquals(1, m.sourceUrls.size()); assertEquals(1, m.importedAssets.size());
    }
}
