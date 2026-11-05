package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.delivery.application.PlanMaterialsService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.identity.domain.model.*;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlanMaterialsTest {
    final TenantId tenant=new TenantId("tenant");final PlanId id=new PlanId("local");final AccountId account=new AccountId("account");
    final String qc="1878659667855539",material="7694973799313178650",product="3841021345144111165";
    final ObjectMapper json=new ObjectMapper();final AdPlanRepository plans=mock(AdPlanRepository.class);final AdPlan plan=mock(AdPlan.class);
    final OauthAccountRepository accounts=mock(OauthAccountRepository.class);final QianchuanCredentialFacade credentials=mock(QianchuanCredentialFacade.class);final AdManagementPort port=mock(AdManagementPort.class);
    final Credential credential=new Credential("fixture",new AdvertiserId("123"));final LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));final PlanStatisticsRange range=new PlanStatisticsRange(today.minusDays(1),today);
    PlanMaterialsService service;final AssetUploadService assets=mock(AssetUploadService.class);
    @BeforeEach void setup() {
        when(plans.findById(tenant,id)).thenReturn(Optional.of(plan));when(plan.status()).thenReturn(PlanStatus.PAUSED);when(plan.accountId()).thenReturn(account);when(plan.qcPlanId()).thenReturn(Optional.of(new QcPlanId(qc)));
        when(accounts.findById(tenant,account)).thenReturn(Optional.of(new OauthAccount(account,tenant,Platform.QIANCHUAN,new AdvertiserId("123"),"账户",null,null,Instant.now().plusSeconds(3600),null,"ACTIVE",List.of())));
        when(credentials.getUsableCredential(tenant,account)).thenReturn(credential);when(port.materialReport(any(),anyString(),any(),any(),anyString())).thenReturn(List.of());
        when(port.materialMetadata(any(),anyString(),anyList())).thenReturn(List.of());when(port.materialAttributes(any(),anyList())).thenReturn(List.of());service=new PlanMaterialsService(plans,accounts,credentials,port,assets);
    }
    com.fasterxml.jackson.databind.node.ObjectNode row(String type) {
        var row=json.createObjectNode().put("material_status","DELIVERY_OK").put("audit_status","AUDIT_PASS");var info=row.putObject("material_info").put("material_type",type);
        var sub=info.putObject(switch(type){case "IMAGE"->"image_material";case "CAROUSEL"->"carousel_material";case "TITLE"->"title_material";case "LIVE_ROOM"->"room_material";default->"video_material";});sub.put("material_id",Long.parseLong(material)).put("title","generated.mp4");
        row.putArray("product_info").addObject().put("product_id",Long.parseLong(product)).put("product_name","棉鞋").put("product_image","https://example.test/product.jpg");row.putArray("product_id_list").add(Long.parseLong(product));
        row.putObject("stats_info").put("product_show_count_for_roi2",12).put("product_click_count_for_roi2",0).put("stat_cost_for_roi2",0);return row;
    }
    void report(String type,JsonNode... rows) {when(port.materialReport(credential,qc,range.startDate(),range.endDate(),type)).thenReturn(List.of(rows));}
    @Test void editingPreviewFiltersHistoricalRowsAgainstCurrentPhysicalPlanBindings() {
        var old=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)old.path("material_info").path("video_material")).put("video_id","old-video");
        var keep=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)keep.path("material_info").path("video_material")).put("material_id",Long.parseLong(material)+1).put("video_id","current-video");
        report("VIDEO",old,keep);
        var live=json.createObjectNode().put("ad_id",qc);var group=live.putArray("multi_product_creative_list").addObject().put("product_id",product);
        group.putArray("video_material").addObject().put("video_id","current-video").put("video_cover_id","current-cover");
        group.putArray("title_material").addObject().put("title","当前标题").put("title_type","CUSTOM");
        when(port.detail(credential,qc)).thenReturn(live);
        assertEquals(1,service.materials(tenant,id,range,"VIDEO",true).records().size());
        assertEquals(2,service.materials(tenant,id,range,"VIDEO").records().size());
        var titles=service.materials(tenant,id,range,"TITLE",true);
        assertEquals(List.of("当前标题"),titles.records().stream().map(PlanMaterialsService.Material::text).toList());
        verify(port,never()).materialReport(eq(credential),eq(qc),any(),any(),eq("TITLE"));
    }
    @Test void currentPreviewRequiresCompleteLiveBindingsRatherThanFallingBackToHistoricalStatistics() {
        report("VIDEO",row("VIDEO"));when(port.detail(credential,qc)).thenReturn(json.createObjectNode().put("ad_id",qc));
        assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO",true));
        verify(port,never()).materialReport(any(),any(),any(),any(),any());
    }
    @Test void nativeStatsKeepExactIdsAndActualZeroWhileUnknownMetricsStayNull() {report("VIDEO",row("VIDEO"));var result=service.materials(tenant,id,range,"VIDEO");var r=result.records().getFirst();assertEquals(material,r.id());assertEquals(product,r.products().getFirst().id());assertEquals(new BigDecimal("12"),r.metrics().get("shows"));assertEquals(BigDecimal.ZERO,r.metrics().get("cost"));assertNull(r.metrics().get("orders"));verify(port).materialReport(credential,qc,range.startDate(),range.endDate(),"VIDEO");}
    @Test void nativeMetadataIsMatchedByIdAndNeverByFilenameOrLatestLocalMaterial() {report("VIDEO",row("VIDEO"));var right=json.createObjectNode().put("material_id",material).put("filename","native.mp4").put("create_time","2026-10-10 17:30:00").put("url","https://example.test/video.mp4").put("poster_url","https://example.test/cover.jpg").put("is_ai_create",true);var wrong=right.deepCopy().put("material_id","1234").put("url","https://example.test/wrong.mp4");when(port.materialMetadata(credential,"VIDEO",List.of(material))).thenReturn(List.of(wrong,right));var r=service.materials(tenant,id,range,"VIDEO").records().getFirst();assertEquals("https://example.test/video.mp4",r.previewUrl());assertEquals("2026-10-10 17:30:00",r.createTime());assertTrue(r.aigc());}
    @Test void attributesProvideRealFirstPublishTag() {report("VIDEO",row("VIDEO"));when(port.materialAttributes(credential,List.of(material))).thenReturn(List.of(json.createObjectNode().put("material_id",material).put("is_first_publish_material",true)));assertEquals(List.of("FIRST_PUBLISH_MATERIAL"),service.materials(tenant,id,range,"VIDEO").records().getFirst().tags());}
    @Test void metadataFailurePreservesNativeStatsWithAnExplicitWarning() {report("VIDEO",row("VIDEO"));when(port.materialMetadata(any(),anyString(),anyList())).thenThrow(new BizException(1406,"素材库权限不足"));var r=service.materials(tenant,id,range,"VIDEO");assertEquals(1,r.records().size());assertTrue(r.warning().contains("权限不足"));assertNull(r.records().getFirst().createTime());}
    @Test void deletedMaterialsRemainVisibleForHistoricalStatistics() {report("VIDEO",row("VIDEO").put("is_delete",true));var r=service.materials(tenant,id,range,"VIDEO").records().getFirst();assertTrue(r.deleted());assertEquals(new BigDecimal("12"),r.metrics().get("shows"));}
    @ParameterizedTest @ValueSource(strings={"IMAGE","CAROUSEL","TITLE","OTHER"}) void everyNativeSubtypeUsesItsOwnReport(String type) {String nativeType=type.equals("OTHER")?"LIVE_ROOM":type;report(nativeType,row(nativeType));var r=service.materials(tenant,id,range,type).records().getFirst();assertEquals(nativeType,r.type());assertEquals(material,r.id());}
    @Test void aigcIncludesOnlyPlatformConfirmedAiAndQueriesAllVisualTypes() {var video=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)video.path("material_info").path("video_material")).put("is_ai_create",true);report("VIDEO",video);report("IMAGE",row("IMAGE"));var r=service.materials(tenant,id,range,"AIGC");assertEquals(1,r.records().size());verify(port).materialReport(credential,qc,range.startDate(),range.endDate(),"CAROUSEL");assertTrue(r.warning().contains("AI 标识"));}
    @Test void carouselGalleryAndMusicUseNativeFiles() {var r=row("CAROUSEL");var sub=(com.fasterxml.jackson.databind.node.ObjectNode)r.path("material_info").path("carousel_material");sub.putArray("image").addObject().put("image_url","https://example.test/a.jpg");sub.put("music_url","https://example.test/music.mp3");report("CAROUSEL",r);var m=service.materials(tenant,id,range,"CAROUSEL").records().getFirst();assertEquals(List.of("https://example.test/a.jpg"),m.imageUrls());assertEquals(m.imageUrls().getFirst(),m.posterUrl());assertEquals("https://example.test/music.mp3",m.musicUrl());}
    @Test void foreignTenantAndDeletedPlanNeverReachPlatform() {assertThrows(BizException.class,()->service.materials(new TenantId("other"),id,range,"VIDEO"));when(plan.status()).thenReturn(PlanStatus.DELETED);assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO"));verifyNoInteractions(port);}
    @Test void draftAndMissingAuthorizationAreRejected() {when(plan.qcPlanId()).thenReturn(Optional.empty());assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO"));when(plan.qcPlanId()).thenReturn(Optional.of(new QcPlanId(qc)));when(accounts.findById(tenant,account)).thenReturn(Optional.empty());assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO"));verifyNoInteractions(port);}
    @Test void mismatchedPlanAndTypeAndDuplicateRowsFailClosed() {report("VIDEO",row("VIDEO").put("ad_id","123"));assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO"));report("VIDEO",row("IMAGE"));assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO"));report("VIDEO",row("VIDEO"),row("VIDEO"));assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO"));}
    @Test void unknownCategoryRejectedWithoutPlatformAccess() {assertThrows(BizException.class,()->service.materials(tenant,id,range,"BAD"));verifyNoInteractions(port);}
    @Test void nativeFailureIsNotMisrepresentedAsEmptySuccess() {when(port.materialReport(any(),anyString(),any(),any(),anyString())).thenThrow(new BizException(1406,"权限不足"));assertThrows(BizException.class,()->service.materials(tenant,id,range,"VIDEO"));}
    @Test void carouselLibraryRestoresAllImagesAudioAndItsActualCreateTime() {report("CAROUSEL",row("CAROUSEL"));var meta=json.createObjectNode().put("material_id",material).put("create_time","2026-10-10 17:48:32");var images=meta.putArray("images");for(int i=0;i<6;i++)images.addObject().put("url","https://example.test/image"+i+".jpg");meta.putObject("audio").put("url","https://example.test/voice.mp3");when(port.materialMetadata(credential,"CAROUSEL",List.of(material))).thenReturn(List.of(meta));var m=service.materials(tenant,id,range,"CAROUSEL").records().getFirst();assertEquals(6,m.imageUrls().size());assertEquals("https://example.test/voice.mp3",m.musicUrl());assertEquals("2026-10-10 17:48:32",m.createTime());}
    @Test void knownUnsupportedOtherTypeIsExplainedButUnrelatedErrorsStayFailures() {when(port.materialReport(credential,qc,range.startDate(),range.endDate(),"LIVE_ROOM")).thenThrow(new BizException(1406,"商品全域投放不支持直播间画面"));var r=service.materials(tenant,id,range,"OTHER");assertTrue(r.records().isEmpty());assertTrue(r.warning().contains("商品全域"));doThrow(new BizException(1406,"权限不足")).when(port).materialReport(credential,qc,range.startDate(),range.endDate(),"LIVE_ROOM");assertThrows(BizException.class,()->service.materials(tenant,id,range,"OTHER"));}
    @Test void browserPreviewUsesOnlyTheExactUploadedObjectIdInTheSelectedAccount() {var v=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)v.path("material_info").path("video_material")).put("video_id","native-video-id").put("url","https://example.test/qc.mp4");report("VIDEO",v);when(assets.platformPreviewUrl(tenant,account.value(),"VIDEO","native-video-id")).thenReturn(Optional.of("https://example.test/uploaded-version.mp4"));assertEquals("https://example.test/uploaded-version.mp4",service.materials(tenant,id,range,"VIDEO").records().getFirst().previewUrl());verify(assets).platformPreviewUrl(tenant,account.value(),"VIDEO","native-video-id");}
    @Test void carouselAudioUsesItsUploadedAudioIdAndKeepsNativePictures() {report("CAROUSEL",row("CAROUSEL"));var meta=json.createObjectNode().put("material_id",material);meta.putObject("audio").put("video_id","native-audio-id").put("url","https://example.test/qc.mp3");meta.putArray("images").addObject().put("url","https://example.test/native-image.jpg");when(port.materialMetadata(credential,"CAROUSEL",List.of(material))).thenReturn(List.of(meta));when(assets.platformPreviewUrl(tenant,account.value(),"AUDIO","native-audio-id")).thenReturn(Optional.of("https://example.test/uploaded.mp3"));var m=service.materials(tenant,id,range,"CAROUSEL").records().getFirst();assertEquals("https://example.test/uploaded.mp3",m.musicUrl());assertEquals(List.of("https://example.test/native-image.jpg"),m.imageUrls());}
    JsonNode bindings(String type,String productId,String objectId,String title) {
        var detail=json.createObjectNode().put("ad_id",qc);var group=detail.putArray("multi_product_creative_list").addObject().put("product_id",productId);
        if(type.equals("VIDEO"))group.putArray("video_material").addObject().put("video_id",objectId).put("video_cover_id","tos/selected-cover");
        if(type.equals("IMAGE"))group.putArray("image_material").addObject().putArray("image_ids").add(objectId);
        if(type.equals("CAROUSEL"))group.putArray("carousel_material").addObject().put("carousel_id",objectId);
        group.putArray("title_material").addObject().put("title",title).put("title_type",type.equals("IMAGE")?"COMMODITY_CARD":"CUSTOM");
        return detail;
    }
    @Test void videoUsesPlanSelectedCoverAndBoundAdvertisingTitleInsteadOfFilenameAndVideoFrame() {
        var video=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)video.path("material_info").path("video_material")).put("video_id","exact-video").putObject("cover_image").put("image_url","https://example.test/frame.jpg");
        report("VIDEO",video);when(port.detail(credential,qc)).thenReturn(bindings("VIDEO",product,"exact-video","跑步背包不晃，出门更轻松"));
        when(port.materialImageMetadata(credential,List.of("tos/selected-cover"))).thenReturn(List.of(json.createObjectNode().put("id","tos/selected-cover").put("url","https://example.test/selected-cover.jpg")));
        var m=service.materials(tenant,id,range,"VIDEO").records().getFirst();assertEquals("https://example.test/selected-cover.jpg",m.posterUrl());
        assertEquals("跑步背包不晃，出门更轻松",m.name());assertEquals(List.of(m.name()),m.titles());assertNull(m.text());
    }
    @Test void imageUsesCommodityTitleAndDoesNotPickTheCustomVideoTitle() {
        var image=row("IMAGE");((com.fasterxml.jackson.databind.node.ObjectNode)image.path("material_info").path("image_material")).put("image_id","tos/exact-image");report("IMAGE",image);
        var detail=(com.fasterxml.jackson.databind.node.ObjectNode)bindings("IMAGE",product,"tos/exact-image","多功能轻便双肩包");
        ((com.fasterxml.jackson.databind.node.ArrayNode)detail.path("multi_product_creative_list").get(0).path("title_material")).addObject().put("title_type","CUSTOM").put("title","视频专用标题");
        when(port.detail(credential,qc)).thenReturn(detail);var m=service.materials(tenant,id,range,"IMAGE").records().getFirst();
        assertEquals("多功能轻便双肩包",m.name());assertEquals(List.of(m.name()),m.titles());
    }
    @Test void unrelatedProductOrObjectOrPlanCannotSupplyCoverOrTitle() {
        var video=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)video.path("material_info").path("video_material")).put("video_id","exact-video");report("VIDEO",video);
        for(var detail:List.of(bindings("VIDEO","other-product","exact-video","错误标题"),bindings("VIDEO",product,"other-video","错误标题"),
                ((com.fasterxml.jackson.databind.node.ObjectNode)bindings("VIDEO",product,"exact-video","错误标题")).put("ad_id","999"))) {
            when(port.detail(credential,qc)).thenReturn(detail);var m=service.materials(tenant,id,range,"VIDEO").records().getFirst();
            assertTrue(m.titles().isEmpty());assertEquals("棉鞋",m.name());assertNull(m.posterUrl());
        }
        verify(port,never()).materialImageMetadata(any(),any());
    }
    @Test void failedCoverLookupRetainsNativeThumbnailAndWarnsWithoutLosingTheMaterial() {
        var video=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)video.path("material_info").path("video_material")).put("video_id","exact-video").putObject("cover_image").put("image_url","https://example.test/frame.jpg");report("VIDEO",video);
        when(port.detail(credential,qc)).thenReturn(bindings("VIDEO",product,"exact-video","实际标题"));
        when(port.materialImageMetadata(any(),any())).thenThrow(new BizException(1406,"封面读取失败"));
        var result=service.materials(tenant,id,range,"VIDEO");assertEquals(1,result.records().size());assertEquals("https://example.test/frame.jpg",result.records().getFirst().posterUrl());assertTrue(result.warning().contains("封面"));
    }
    @Test void carouselPrefersRenewedLibraryAudioOverExpiredReportVodAndPreservesItsOwnTitle() {
        var carousel=row("CAROUSEL");((com.fasterxml.jackson.databind.node.ObjectNode)carousel.path("material_info").path("carousel_material")).put("title","运动出门，随身小物不乱放").put("music_url","https://example.test/expired-vod.mp3");report("CAROUSEL",carousel);
        var meta=json.createObjectNode().put("material_id",material);meta.putObject("audio").put("url","https://example.test/renewed-audio.mp3");meta.putArray("images").addObject().put("url","https://example.test/carousel.jpg");
        when(port.materialMetadata(credential,"CAROUSEL",List.of(material))).thenReturn(List.of(meta));when(port.detail(credential,qc)).thenReturn(bindings("CAROUSEL",product,material,"其他组合标题"));
        var m=service.materials(tenant,id,range,"CAROUSEL").records().getFirst();assertEquals("https://example.test/renewed-audio.mp3",m.musicUrl());
        assertEquals("运动出门，随身小物不乱放",m.name());assertEquals(List.of(m.name()),m.titles());assertEquals(List.of("https://example.test/carousel.jpg"),m.imageUrls());
    }
    @Test void exactPublishedLabelRestoresOriginalNameAndOrdersOnlyActualNativeCopy() {
        var video=row("VIDEO");((com.fasterxml.jackson.databind.node.ObjectNode)video.path("material_info").path("video_material")).put("video_id","exact-video");report("VIDEO",video);
        when(port.detail(credential,qc)).thenReturn(bindings("VIDEO",product,"exact-video","实际投放文案"));
        var labels=mock(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanMaterialPresentationStore.class);service.presentationStore(labels);
        when(labels.label(eq(tenant),eq(account),eq(qc),eq("VIDEO"),eq("exact-video"),eq(Set.of(product)),eq(List.of("实际投放文案"))))
                .thenReturn(Optional.of(new com.lynx.ad.modules.delivery.infrastructure.persistence.PlanMaterialPresentationStore.Label("原始生成素材名称",List.of("实际投放文案"))));
        var result=service.materials(tenant,id,range,"VIDEO").records().getFirst();
        assertEquals("原始生成素材名称",result.name());assertEquals(List.of("实际投放文案"),result.titles());
    }
}
