package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.application.*;
import com.lynx.ad.modules.creative.infrastructure.persistence.AssetUploadRepository.*;
import com.lynx.ad.modules.delivery.application.CreationPayloadBuilder;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.identity.domain.model.Credential;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreationPayloadTest {
    final AssetUploadService assets=mock(AssetUploadService.class);
    final CarouselPlatformService carousels=mock(CarouselPlatformService.class);
    final CreationQianchuanGateway gateway=mock(CreationQianchuanGateway.class);
    final CreationPayloadBuilder builder=new CreationPayloadBuilder(new ObjectMapper(),assets,carousels,gateway);
    CreationState s;
    @BeforeEach void prepare(){
        s=new CreationState();s.planId="plan";s.tenantId="tenant";s.accountId="account";s.awemeId="9007199254740993";s.name="真实投放";
        s.budget=new BigDecimal("600.12");s.roi2Goal=new BigDecimal("5.12");s.analysisStatus="SUCCEEDED";s.confirmedVersion=s.packageVersion;
        s.products.add(new CreationState.Product("9007199254740995","9007199254740995","商品",null,null,null,"",null,List.of(),List.of()));
        var m=new CreationState.Material();m.id="material";m.productId="9007199254740995";m.type="VIDEO";m.textAssetId="text";m.videoAssetId="video";m.coverAssetId="cover";m.titles=List.of("商品实拍展示");s.materials.add(m);
        when(gateway.credential(any(),eq("account"),eq(s.awemeId))).thenReturn(new Credential("test",new AdvertiserId("9007199254740997")));
        when(assets.detail(any(),eq("video"))).thenReturn(asset("video","VIDEO","APPROVED","account","UPLOADED","real-video-id"));
        when(assets.detail(any(),eq("cover"))).thenReturn(asset("cover","IMAGE","APPROVED","account","UPLOADED","real-cover-id"));
        when(assets.detail(any(),eq("text"))).thenReturn(asset("text","TEXT","APPROVED",null,null,null));
    }
    static AssetUploadService.Detail asset(String id,String kind,String review,String account,String sync,String object){
        var r=new Review(id,"tenant",review,null,null,"",List.of(),null,1,Instant.now());
        var targets=account==null?List.<Sync>of():List.of(new Sync("sync","tenant",id,account,"123",sync,object,null,null,"UNKNOWN",1,null,null,null));
        return new AssetUploadService.Detail(id,kind,id,10,"STORED","oss",false,"md5",1080,"IMAGE".equals(kind)?1080:1920,"标题","url",null,"2026-10-08",targets,r);
    }
    @Test void nonSquareCardIsRejectedEvenIfItWasAlreadyUploadedSuccessfully() {
        var image=asset("image","IMAGE","APPROVED","account","UPLOADED","native-image");
        var portrait=new AssetUploadService.Detail(image.id(),image.kind(),image.filename(),image.sizeBytes(),image.storageStatus(),image.storageMode(),false,image.md5(),1080,1920,image.textContent(),image.url(),null,image.createdAt(),image.syncs(),image.aiReview());
        var m=s.materials.getFirst();m.type="IMAGE";m.imageAssetIds=List.of("image");m.imageMode="SQUARE";
        when(assets.detail(any(),eq("image"))).thenReturn(portrait);
        assertTrue(assertThrows(BizException.class,()->builder.build(s)).getMessage().contains("1:1"));
        when(assets.detail(any(),eq("image"))).thenReturn(image);
        assertDoesNotThrow(()->builder.build(s));
    }
    @Test void costPayloadKeepsIntegerIdsAndAccountSpecificRealMedia(){
        var body=builder.build(s);assertTrue(body.path("advertiser_id").isIntegralNumber());assertEquals("9007199254740997",body.path("advertiser_id").asText());
        assertEquals("SMART_BID_CUSTOM",body.path("delivery_setting").path("smart_bid_type").asText());assertEquals(new BigDecimal("5.12"),body.path("delivery_setting").path("roi2_goal").decimalValue());
        var c=body.path("multi_product_creative_list").get(0);assertEquals("real-video-id",c.path("video_material").get(0).path("video_id").asText());assertTrue(c.path("hide_in_aweme").asBoolean());
        assertFalse(body.has("access_token"));assertFalse(body.has("budget"));
    }
    @ParameterizedTest @ValueSource(strings={"0","50","99.99","100","100.01","299.99"})
    void costBudgetBelow300CannotReachMediaOrPublish(String amount) {
        s.budget=new BigDecimal(amount);
        assertTrue(assertThrows(BizException.class,()->builder.build(s)).getMessage().contains("控成本日预算不能低于 300 元"));
        verify(assets,never()).detail(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"300","300.00","300.01","600.12"})
    void costBudgetAtOrAbove300KeepsTheUserAmount(String amount) {
        s.budget=new BigDecimal(amount);
        assertEquals(0,s.budget.compareTo(builder.build(s).path("delivery_setting").path("budget").decimalValue()));
    }
    @Test void volumeHasNoRoiGoalOrFabricatedEstimates(){
        s.smartBidType="CONSERVATIVE";var setting=builder.build(s).path("delivery_setting");
        assertEquals("SMART_BID_CONSERVATIVE",setting.path("smart_bid_type").asText());assertFalse(setting.has("roi2_goal"));assertFalse(setting.has("estimate_convert"));assertFalse(setting.has("estimate_roi_goal"));
    }
    @Test void alwaysVisibleIsSentAsAnExplicitFalseBoolean() {
        s.hideInAweme=false;
        var value=builder.build(s).path("multi_product_creative_list").get(0).path("hide_in_aweme");
        assertTrue(value.isBoolean());assertFalse(value.booleanValue());
        s.mediaChanged();assertThrows(BizException.class,()->builder.build(s));
    }
    @Test void volumeUsesOnlyAnExactPlatformBudgetRecommendation() throws Exception {
        s.smartBidType="CONSERVATIVE";s.recommendations=new ObjectMapper().readTree("{\"accountId\":\"account\",\"awemeId\":\"9007199254740993\",\"productIds\":[\"9007199254740995\"],\"budget\":{\"estimate_trans_cost_list\":[{\"budget\":600.12,\"estimate_convert\":7,\"min_estimate_convert\":6,\"estimate_roi\":3.92,\"min_estimate_roi\":3.14}]}}");
        var setting=builder.build(s).path("delivery_setting");assertEquals(7,setting.path("estimate_convert").asInt());assertEquals(3.14,setting.path("min_estimate_roi_goal").asDouble());
        s.budget=new BigDecimal("601");assertFalse(builder.build(s).path("delivery_setting").has("estimate_convert"));
    }
    @Test void explicitDatesAreSentAndPastDatesRejected(){
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));s.scheduleType="DATE_RANGE";s.startDate=today.toString();s.endDate=today.plusDays(20).toString();
        var setting=builder.build(s).path("delivery_setting");assertEquals("SCHEDULE_START_END",setting.path("video_schedule_type").asText());assertEquals(s.endDate,setting.path("end_time").asText());
        s.startDate=today.minusDays(1).toString();assertThrows(BizException.class,()->builder.build(s));
    }
    @Test void changedSelectionOrPackageCannotReuseConfirmation(){
        s.mediaChanged();assertThrows(BizException.class,()->builder.build(s));s.confirmedVersion=s.packageVersion;s.analysisStatus="RUNNING";assertDoesNotThrow(()->builder.build(s));
    }
    @Test void RejectedTextOrOtherAccountMediaBlocksPublish(){
        when(assets.detail(any(),eq("text"))).thenReturn(asset("text","TEXT","REJECTED",null,null,null));assertThrows(BizException.class,()->builder.build(s));
        when(assets.detail(any(),eq("text"))).thenReturn(asset("text","TEXT","APPROVED",null,null,null));
        when(assets.detail(any(),eq("video"))).thenReturn(asset("video","VIDEO","APPROVED","other-account","UPLOADED","video"));assertThrows(BizException.class,()->builder.build(s));
    }
    @Test void EveryProductNeedsItsOwnMaterial(){
        s.products.add(new CreationState.Product("2","2","第二个商品",null,null,null,"",null,List.of(),List.of()));assertThrows(BizException.class,()->builder.build(s));
    }
    @Test void engineReferencesNeedNoReviewButStillNeedAccountSpecificPlatformIds() {
        var a=asset("video","VIDEO","NOT_REQUIRED","account","UPLOADED","real-engine-video");
        var ref=new AssetUploadService.Detail(a.id(),a.kind(),a.filename(),a.sizeBytes(),a.storageStatus(),"engine_oss",true,a.md5(),a.width(),a.height(),a.textContent(),a.url(),null,a.createdAt(),a.syncs(),a.aiReview());
        when(assets.detail(any(),eq("video"))).thenReturn(ref);
        assertEquals("real-engine-video",builder.build(s).path("multi_product_creative_list").get(0).path("video_material").get(0).path("video_id").asText());
        when(assets.detail(any(),eq("video"))).thenReturn(asset("video","VIDEO","NOT_REQUIRED","account","UPLOADED","id"));
        assertThrows(BizException.class,()->builder.build(s));
    }
    @Test void boundAwemeVideoIncludesPromotionCardFromItsSyncedCover() {
        var card=builder.build(s).path("multi_product_creative_list").get(0).path("creative_card");
        assertEquals("real-cover-id",card.path("promotion_card_image_id").asText());
        assertEquals("商品实拍展示",card.path("promotion_card_selling_points").get(0).asText());
        assertEquals("查看详情",card.path("promotion_card_action_button").asText());
        assertFalse(card.path("promotion_card_button_smart_optimization").asBoolean());
        s.materials.getFirst().titles=List.of("这是完整的商品展示标题不应被截断成卖点");
        assertEquals("查看商品详细信息",builder.build(s).path("multi_product_creative_list").get(0).path("creative_card").path("promotion_card_selling_points").get(0).asText());
    }
    @Test void productImagesRequireCommodityTitleEvenForNonSelfAwemeAndEachImageGetsItsOwnMaterial() {
        var m=new CreationState.Material();m.productId=s.products.getFirst().qcProductId();m.type="IMAGE";m.textAssetId="text";m.imageMode="SQUARE";
        m.imageAssetIds=List.of("image-one","image-two");m.titles=List.of("商品卡展示标题");s.materials.add(m);
        when(assets.detail(any(),eq("image-one"))).thenReturn(asset("image-one","IMAGE","APPROVED","account","UPLOADED","real-image-one"));
        when(assets.detail(any(),eq("image-two"))).thenReturn(asset("image-two","IMAGE","APPROVED","account","UPLOADED","real-image-two"));
        when(gateway.commodityCardTitle(any(),any(),any())).thenReturn(false);
        var creative=builder.build(s).path("multi_product_creative_list").get(0);
        assertEquals("COMMODITY_CARD",creative.path("title_material").get(0).path("title_type").asText());
        assertEquals("商品卡展示标题",creative.path("title_material").get(0).path("title").asText());
        assertEquals(2,creative.path("image_material").size());
        for(var image:creative.path("image_material")){assertEquals("SQUARE",image.path("image_mode").asText());assertEquals(1,image.path("image_ids").size());}
        assertEquals("real-image-one",creative.path("creative_card").path("promotion_card_image_id").asText());
        m.imageMode="LARGE";assertThrows(BizException.class,()->builder.build(s));
    }
    @Test void carouselOnlyUsesItsOwnSyncedImageForPromotionCardAndKeepsIntegerCarouselId() {
        var m=s.materials.getFirst();m.type="CAROUSEL";m.carouselId="bundle";m.imageAssetIds=List.of("carousel-image");
        when(carousels.ensure(any(),eq("bundle"),eq("account"))).thenReturn("9007199254740999");
        when(assets.detail(any(),eq("carousel-image"))).thenReturn(asset("carousel-image","IMAGE","APPROVED","account","UPLOADED","carousel-image-id"));
        var creative=builder.build(s).path("multi_product_creative_list").get(0);
        assertEquals("carousel-image-id",creative.path("creative_card").path("promotion_card_image_id").asText());
        assertTrue(creative.path("carousel_material").get(0).path("carousel_id").isIntegralNumber());
        assertEquals("9007199254740999",creative.path("carousel_material").get(0).path("carousel_id").asText());
        when(assets.detail(any(),eq("carousel-image"))).thenReturn(asset("carousel-image","IMAGE","APPROVED","other-account","UPLOADED","foreign-image"));
        assertThrows(BizException.class,()->builder.build(s));
        m.imageAssetIds=List.of();assertTrue(assertThrows(BizException.class,()->builder.build(s)).getMessage().contains("卡片配图"));
    }
    @Test void eachProductGetsItsOwnPromotionImage() {
        s.products.add(new CreationState.Product("2","2","第二个商品",null,null,null,"",null,List.of(),List.of()));
        var m=new CreationState.Material();m.productId="2";m.type="VIDEO";m.textAssetId="text";m.videoAssetId="video";m.coverAssetId="second-cover";m.titles=List.of("第二个商品实拍");s.materials.add(m);
        when(assets.detail(any(),eq("second-cover"))).thenReturn(asset("second-cover","IMAGE","APPROVED","account","UPLOADED","second-cover-id"));
        var creatives=builder.build(s).path("multi_product_creative_list");
        assertEquals("real-cover-id",creatives.get(0).path("creative_card").path("promotion_card_image_id").asText());
        assertEquals("second-cover-id",creatives.get(1).path("creative_card").path("promotion_card_image_id").asText());
    }
    @Test void titleCountIncludesCommodityTitleWithoutCountingTheSameCopyTwice() {
        var m=s.materials.getFirst();when(gateway.commodityCardTitle(any(),any(),any())).thenReturn(true);
        m.titles=java.util.stream.IntStream.range(0,29).mapToObj(i->"商品实拍展示"+i).toList();
        assertEquals(29,builder.build(s).path("multi_product_creative_list").get(0).path("title_material").size());
        m.titles=java.util.stream.IntStream.range(0,30).mapToObj(i->"商品实拍展示"+i).toList();
        var titles=builder.build(s).path("multi_product_creative_list").get(0).path("title_material");
        assertEquals(30,titles.size());assertEquals("COMMODITY_CARD",titles.get(0).path("title_type").asText());
        m.titles=java.util.stream.IntStream.range(0,31).mapToObj(i->"商品实拍展示"+i).toList();
        assertThrows(BizException.class,()->builder.build(s));
        m.titles=java.util.stream.IntStream.range(0,30).mapToObj(i->"商品实拍展示"+i).toList();
        when(gateway.commodityCardTitle(any(),any(),any())).thenReturn(false);
        assertEquals(30,builder.build(s).path("multi_product_creative_list").get(0).path("title_material").size());
    }
    @Test void identicalCopyAcrossMaterialKindsAndTitleTypesIsPublishedOnlyOnce() {
        var video=s.materials.getFirst();video.titles=List.of("秋冬骑行双手不僵","手套防滑触屏实拍");
        var image=new CreationState.Material();image.productId=video.productId;image.type="IMAGE";image.textAssetId="text";
        image.imageAssetIds=List.of("cover");image.titles=List.of("商品卡保暖手套展示"," 秋冬骑行双手不僵 ");s.materials.add(image);
        var carousel=new CreationState.Material();carousel.productId=video.productId;carousel.type="CAROUSEL";carousel.textAssetId="text";
        carousel.carouselId="bundle";carousel.imageAssetIds=List.of("cover");carousel.titles=List.of("商品卡保暖手套展示","手套防滑触屏实拍");s.materials.add(carousel);
        when(carousels.ensure(any(),eq("bundle"),eq("account"))).thenReturn("9007199254740999");
        var titles=builder.build(s).path("multi_product_creative_list").get(0).path("title_material");
        assertEquals(3,titles.size());
        var copies=new LinkedHashSet<String>();var commodityCopies=new ArrayList<String>();
        for(var title:titles){assertTrue(copies.add(title.path("title").asText()),"同一商品的创意标题不得重复，即使 title_type 不同");if("COMMODITY_CARD".equals(title.path("title_type").asText()))commodityCopies.add(title.path("title").asText());}
        assertEquals(Set.of("秋冬骑行双手不僵","手套防滑触屏实拍","商品卡保暖手套展示"),copies);
        assertEquals(List.of("商品卡保暖手套展示"),commodityCopies);
        assertEquals(List.of("商品卡保暖手套展示"," 秋冬骑行双手不僵 "),image.titles,"去重不能改写草稿及已审核的素材文本");
    }
    @Test void singleCopyCanServeAsCommodityTitleWithoutBeingDuplicatedAsCustomTitle() {
        var image=s.materials.getFirst();image.type="IMAGE";image.imageAssetIds=List.of("cover");image.titles=List.of("商品卡展示标题","商品卡展示标题");
        var titles=builder.build(s).path("multi_product_creative_list").get(0).path("title_material");
        assertEquals(1,titles.size());assertEquals("商品卡展示标题",titles.get(0).path("title").asText());assertEquals("COMMODITY_CARD",titles.get(0).path("title_type").asText());
    }
    @Test void tooShortTitlesFailBeforePlatformWriteWithChineseCountedAsTwoCharacters() {
        s.materials.getFirst().titles=List.of("商品实拍");assertThrows(BizException.class,()->builder.build(s));
        s.materials.getFirst().titles=List.of("商品实拍图");assertDoesNotThrow(()->builder.build(s));
        s.materials.getFirst().titles=List.of("123456789");assertThrows(BizException.class,()->builder.build(s));
        s.materials.getFirst().titles=List.of("1234567890");assertDoesNotThrow(()->builder.build(s));
        verify(gateway,never()).create(any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"NONE","QUEUED","RUNNING","FAILED"})
    void advisoryAnalysisDoesNotBlockReadyMediaButCannotBypassProductChecks(String analysis) {
        s.analysisStatus=analysis;assertDoesNotThrow(()->builder.build(s));
        var p=s.products.getFirst();
        s.products.set(0,new CreationState.Product(p.productId(),p.qcProductId(),p.name(),p.price(),p.userPrice(),p.imageUrl(),p.category(),0L,p.tags(),p.unavailableReasons()));
        assertThrows(BizException.class,()->builder.build(s));
        verify(gateway,never()).create(any(),any(),any(),any());
    }

}
