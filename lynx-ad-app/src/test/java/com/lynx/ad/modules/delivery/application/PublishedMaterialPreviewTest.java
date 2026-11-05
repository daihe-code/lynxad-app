package com.lynx.ad.modules.delivery.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.infrastructure.persistence.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import java.util.*;
import org.junit.jupiter.api.*;

class PublishedMaterialPreviewTest {
    final ObjectMapper json=new ObjectMapper();final TenantId tenant=new TenantId("tenant");
    final AdPlanRepository plans=mock(AdPlanRepository.class);final CreationQianchuanGateway gateway=mock(CreationQianchuanGateway.class);
    final CarouselRepository carousels=mock(CarouselRepository.class);
    final PublishedMaterialPreview preview=new PublishedMaterialPreview(plans,gateway,carousels);
    CreationState state;Map<String,AssetUploadService.Detail> files;ObjectNode live;
    @BeforeEach void setup() {
        state=new CreationState();state.tenantId="tenant";state.planId="root";state.accountId="account";state.awemeId="111";state.publishStatus="CREATED";
        state.products.add(new CreationState.Product("123","123","商品",null,null,null,null,null,List.of(),List.of()));
        state.materials.add(material("old"));state.materials.add(material("keep"));
        files=Map.of("old",file("old","VIDEO"),"keep",file("keep","VIDEO"),"cover",file("cover","IMAGE"));
        live=json.createObjectNode().put("ad_id","2002");var group=live.putArray("multi_product_creative_list").addObject().put("product_id",123).put("hide_in_aweme",false);
        group.putArray("video_material").addObject().put("video_id","keep").put("video_cover_id","cover").put("aweme_item_id",0);
        group.putArray("title_material").addObject().put("title","最新标题").put("title_type","CUSTOM");
        var plan=new AdPlan(new PlanId("transferred"),tenant,new AccountId("account"),new AwemeId("111"),new PlanName("计划"),PlanStatus.PAUSED,PlanSource.LINGTOU,
            new DeliverySetting(SmartBidType.CONSERVATIVE,Money.ofYuan(300),Optional.empty(),Schedule.fromNow(),AudienceJson.empty()),List.of(new QcProductId("123")),new QcPlanId("2002"),"DISABLE",null,1,null,List.of());
        when(plans.findById(tenant,new PlanId("transferred"))).thenReturn(Optional.of(plan));
        when(gateway.detail(tenant,"account","111","2002")).thenReturn(live);
    }
    CreationState.Material material(String id){var m=new CreationState.Material();m.id=id;m.type="VIDEO";m.productId="123";m.videoAssetId=id;m.coverAssetId="cover";m.status="READY";m.titles.add("旧标题");return m;}
    AssetUploadService.Detail file(String id,String kind){
        var sync=new AssetUploadRepository.Sync("sync","tenant",id,"account","999","UPLOADED",id,null,null,"ACCEPTED",0,null,null,null);
        return new AssetUploadService.Detail(id,kind,id,10,"STORED","oss",true,null,720,1280,null,"https://assets.test/"+id,null,null,List.of(sync),null);
    }
    @Test void currentPhysicalMemberFiltersOldMediaAndRefreshesTitlesWithoutChangingAuditOrRevision() {
        state.verifiedPlatformCreatives=json.createArrayNode().addObject().put("product_id",123).putArray("video_material").addObject().put("video_id","old");
        var audit=state.verifiedPlatformCreatives.deepCopy();int revision=state.revision;
        preview.project(tenant,"transferred",state,files);
        assertEquals(Boolean.TRUE,state.publishedMaterialsCurrent);assertEquals(List.of("keep"),state.activeMaterials().stream().map(m->m.id).toList());
        assertEquals(List.of("最新标题"),state.titlePools.get("123").customTitles);assertFalse(state.hideInAweme);
        assertEquals(audit,state.verifiedPlatformCreatives);assertEquals(revision,state.revision);
        verify(gateway).detail(tenant,"account","111","2002");verifyNoMoreInteractions(gateway);
    }
    @Test void mediaUnknownToLocalSnapshotUsesCompleteLivePreviewInsteadOfPartialLocalList() {
        ((ObjectNode)live.withArray("multi_product_creative_list").get(0)).withArray("video_material").addObject().put("video_id","external").put("video_cover_id","external-cover");
        preview.project(tenant,"transferred",state,files);
        assertEquals(Boolean.FALSE,state.publishedMaterialsCurrent);assertEquals(2,state.materials.size());
    }
    @Test void failedOrWrongPlanReadCannotLabelHistoricalMediaCurrent() {
        live.put("ad_id","wrong");preview.project(tenant,"transferred",state,files);assertEquals(Boolean.FALSE,state.publishedMaterialsCurrent);
        when(gateway.detail(tenant,"account","111","2002")).thenThrow(new BizException(1406,"unavailable"));
        assertDoesNotThrow(()->preview.project(tenant,"transferred",state,files));assertEquals(Boolean.FALSE,state.publishedMaterialsCurrent);
    }
    @Test void unpublishedDraftDoesNotReadPlatformOrFilterItsSelections() {
        state.publishStatus="NONE";preview.project(tenant,"transferred",state,files);
        assertNull(state.publishedMaterialsCurrent);assertEquals(2,state.materials.size());verifyNoInteractions(plans,gateway,carousels);
    }
    @Test void changedPlatformCoverUsesTheLivePreview() {
        ((ObjectNode)live.path("multi_product_creative_list").get(0).path("video_material").get(0)).put("video_cover_id","new-cover");
        preview.project(tenant,"transferred",state,files);assertEquals(Boolean.FALSE,state.publishedMaterialsCurrent);
    }
}
