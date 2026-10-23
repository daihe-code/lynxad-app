package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import org.junit.jupiter.api.Test;

class CreationViewTest {
    @Test void previouslySavedModelFallbackIsShownAsFailureWithoutRewritingTheStoredDraft() throws Exception {
        var json=new ObjectMapper();var state=new CreationState();state.analysisStatus="SUCCEEDED";
        state.analysis=json.readTree("{\"processing\":{\"inference_error\":\"ValueError\"},\"selection_analysis\":{}}");
        var view=CreationView.of(state);
        assertEquals("FAILED",view.analysisStatus());assertTrue(view.analysisError().contains("校验未通过"));
        assertEquals("SUCCEEDED",state.analysisStatus);assertNull(state.analysisError);
        state.analysisStatus="RUNNING";
        assertEquals("RUNNING",CreationView.of(state).analysisStatus());
    }
    @Test void generatedVideoImageAndCarouselNamesShowOnlyProductNameWithoutChangingIdsOrAdvertisingText() throws Exception {
        var state=new CreationState();state.products.add(new CreationState.Product("123","123","20寸折叠自行车",null,null,null,null,null,java.util.List.of(),java.util.List.of()));
        for(String type:java.util.List.of("VIDEO","VIDEO","IMAGE","CAROUSEL")) {
            var material=new CreationState.Material();material.id="material-"+state.materials.size();material.engineMaterialId=material.id;
            material.productId="123";material.type=type;material.title="同一个广告标题";material.titles=java.util.List.of(material.title);state.materials.add(material);
        }
        var view=CreationView.of(state);var names=view.materials().stream().map(CreationView.MaterialView::displayName).toList();
        assertEquals(java.util.List.of("20寸折叠自行车","20寸折叠自行车","20寸折叠自行车","20寸折叠自行车"),names);
        assertEquals(state.materials.stream().map(m->m.id).toList(),view.materials().stream().map(CreationView.MaterialView::id).toList());
        assertTrue(view.materials().stream().allMatch(m->m.title().equals("同一个广告标题")&&m.titles().equals(java.util.List.of("同一个广告标题"))));
        state.materials.removeFirst();state.materials.getFirst().title="修改后的广告标题";
        var restored=new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(state),CreationState.class);
        assertEquals(names.get(1),CreationView.of(restored).materials().getFirst().displayName());
    }
    @Test void manualMaterialNamesRemainTheUsersOwnTextAndUnicodeNamesAreNotSplit() {
        var state=new CreationState();state.products.add(new CreationState.Product("123","123","🚲".repeat(24),null,null,null,null,null,java.util.List.of(),java.util.List.of()));
        var generated=new CreationState.Material();generated.id="ai";generated.type="IMAGE";generated.productId="123";generated.engineMaterialId="ai";state.materials.add(generated);
        var manual=new CreationState.Material();manual.id="manual";manual.title="用户上传的图片名称";state.materials.add(manual);
        var view=CreationView.of(state);assertTrue(view.materials().getFirst().displayName().contains("🚲".repeat(20)+"…"));
        assertEquals("用户上传的图片名称",view.materials().getLast().displayName());assertNull(manual.engineMaterialId);
    }
    @Test void deliverySourceSurvivesStorageAndIsVisibleWithoutInternalCertificates() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules();var s=new CreationState();
        var m=new CreationState.Material();m.id="base";m.type="IMAGE";
        m.deliverySource="product_reference";m.deliveryMode="image_only";m.deliveryFamilyId="family";
        m.typographyStatus="pending";
        s.materials.add(m);
        var restored=json.readValue(json.writeValueAsString(s),CreationState.class);
        var browser=json.valueToTree(CreationView.of(restored));
        assertEquals("product_reference",browser.path("materials").get(0).path("deliverySource").asText());
        assertEquals("image_only",browser.path("materials").get(0).path("deliveryMode").asText());
        assertEquals("pending",browser.path("materials").get(0).path("typographyStatus").asText());
    }
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
