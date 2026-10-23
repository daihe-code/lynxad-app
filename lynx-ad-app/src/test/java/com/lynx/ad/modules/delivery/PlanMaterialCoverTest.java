package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.domain.service.PlanMaterialCover;
import org.junit.jupiter.api.Test;

class PlanMaterialCoverTest {
    final ObjectMapper json=new ObjectMapper();
    CreationState.Material material(String id,String type,String status,String cover){
        var material=new CreationState.Material();material.id=id;material.type=type;material.status=status;material.coverAssetId=cover;return material;
    }
    @Test void localCoverUsesStoredImageAssetInsteadOfEngineUrlsOrProductReferences(){
        var state=new CreationState();var video=material("video","VIDEO","READY",null);
        video.posterUrl="https://engine.test/signed?token=not-for-browser";
        video.imageUrls.add("https://shop.test/product.jpg");
        state.materials.add(video);
        assertNull(PlanMaterialCover.localAsset(state));
        video.coverAssetId="stored-cover";
        assertEquals("stored-cover",PlanMaterialCover.localAsset(state));
    }
    @Test void ignoresFailedRemovedAndTitleMaterialsAndSupportsImageGroups(){
        var state=new CreationState();state.materials.add(material("failed","VIDEO","FAILED","bad-cover"));
        state.materials.add(material("removed","IMAGE","READY","old-cover"));state.removedMaterialIds.add("removed");
        state.materials.add(material("title","TITLE","READY","not-a-cover"));
        var carousel=material("carousel","CAROUSEL","READY",null);carousel.imageAssetIds.add("carousel-image");state.materials.add(carousel);
        assertEquals("carousel-image",PlanMaterialCover.localAsset(state));
    }
    @Test void platformCoverComesOnlyFromVisualCreativeMetadata() throws Exception {
        var payload=json.readTree("""
            {"product_info":[{"product_image":"https://shop.test/product.jpg"}],
             "multi_product_creative_list":[{"product_id":123,"video_material":[
               {"video_id":"video","url":"https://cdn.test/video.mp4","cover_image":{"image_url":"https://cdn.test/cover.jpg"}}]}]}
            """);
        assertEquals("https://cdn.test/cover.jpg",PlanMaterialCover.remote(payload));
        assertNull(PlanMaterialCover.remote(payload.path("product_info")));
    }
    @Test void absentOrUnusablePlatformCoverRemainsUnknown() throws Exception {
        assertNull(PlanMaterialCover.remote(null));
        assertNull(PlanMaterialCover.remote(json.readTree("""
            {"product_info":[{"product_image":"https://shop.test/product.jpg"}],"multi_product_creative_list":[
             {"video_material":[{"cover_url":"javascript:invalid","url":"https://cdn.test/video.mp4"}]}]}
            """)));
    }
}
