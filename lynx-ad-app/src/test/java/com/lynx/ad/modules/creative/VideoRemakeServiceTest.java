package com.lynx.ad.modules.creative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.GenerationCharges;
import com.lynx.ad.modules.billing.application.WorkspacePricing;
import com.lynx.ad.modules.creative.application.AssetUploadService;
import com.lynx.ad.modules.creative.application.CreationAssetImporter;
import com.lynx.ad.modules.creative.application.VideoRemakeService;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.creative.infrastructure.adapter.VideoRemakeSchedulingConfiguration;
import com.lynx.ad.modules.creative.infrastructure.persistence.VideoRemakeStore;
import com.lynx.ad.modules.creative.infrastructure.persistence.VideoRemakeStore.Task;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.domain.model.CreationState.Product;
import com.lynx.ad.modules.identity.application.AccountBindingRegistry;
import com.lynx.ad.modules.tenant.application.WorkspaceAccess;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class VideoRemakeServiceTest {
    private static final String ID = "10000000-0000-0000-0000-000000000001";
    private static final String ENGINE = "rpt_11111111111111111111111111111111";
    private static final String TASK_PATH = "/tasks/" + ENGINE;
    private static final BigDecimal RATE = BigDecimal.valueOf(240);
    private final ObjectMapper json = new ObjectMapper();
    private final CreationEngineClient engine = mock(CreationEngineClient.class);
    private final VideoRemakeStore store = mock(VideoRemakeStore.class);
    private final GenerationCharges charges = mock(GenerationCharges.class);
    private final CreationQianchuanGateway catalog = mock(CreationQianchuanGateway.class);
    private final VideoRemakeService service = new VideoRemakeService(engine, catalog,
            mock(AccountBindingRegistry.class), mock(WorkspaceAccess.class), charges, new WorkspacePricing(), store,
            mock(CreationAssetImporter.class), mock(AssetUploadService.class), new TransactionTemplate() {
                @Override public <T> T execute(TransactionCallback<T> action) {
                    return action.doInTransaction(new SimpleTransactionStatus());
                }
            }, json);

    @Test
    void schedulerIsRegisteredWhenItsConfigurationIsComponentScanned() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new AssignableTypeFilter(VideoRemakeSchedulingConfiguration.class));
            scanner.scan(VideoRemakeSchedulingConfiguration.class.getPackageName());
            context.refresh();
            assertEquals("video-remake-",
                    context.getBean("videoRemakeScheduler", ThreadPoolTaskScheduler.class).getThreadNamePrefix());
        }
    }

    @Test
    void capabilityReturnsSpendablePointsAndSourceMatching() {
        var available = new BigDecimal("137.5");
        when(charges.access("tenant")).thenReturn(new GenerationCharges.Access(true, GenerationCharges.BalanceStatus.LOW, available));
        when(engine.remake("GET", "/capabilities", null)).thenReturn(json.createObjectNode()
                .put("ready", true).put("review_worker_ready", true).put("kling_4_available", true)
                .put("source_matching_available", true).put("source_matching_hd", true));

        var capability = service.capability("tenant");
        assertEquals(available, capability.availablePoints());
        assertEquals(0, RATE.compareTo(capability.points()));
        assertTrue(capability.ready());
        assertTrue(capability.sourceMatchingAvailable());
        assertTrue(capability.sourceMatchingHd());
        assertEquals(Map.of("720p", BigDecimal.valueOf(16), "1080p", BigDecimal.valueOf(24)), capability.pointsPerSecond());
    }

    @Test
    void reviewingIsFreeAndApprovalReservesExactlyOnce() throws Exception {
        var review = row("REVIEW");
        when(store.find("tenant", ID, false)).thenReturn(Optional.of(review), Optional.of(review),
                Optional.of(row("CONFIRMING")));
        when(store.find("tenant", ID, true)).thenReturn(Optional.of(review));
        when(engine.remake("GET", TASK_PATH + "?store_id=tenant", null)).thenReturn(json.readTree("""
                {"id":"%s","store_id":"tenant","status":"awaiting_confirmation","ad_notices":["请按说明存放"],
                 "output_spec":{"width":1920,"height":1080,"duration_seconds":15},"plan":{
                   "target_product_id":"p1","title":"原分镜","shots":[{"scene_id":"source-1","duration":15}]}}
                """.formatted(ENGINE)));
        when(charges.quote("tenant", "remake:" + ID)).thenReturn(
                new GenerationCharges.Quote(null, Map.of("video", 1), Map.of("video", RATE)));

        var view = service.get("tenant", "actor", ID);
        assertEquals("REVIEW", view.phase());
        assertEquals(List.of("请按说明存放"), view.adNotices());
        assertEquals(1920, view.outputSpec().path("width").asInt());
        assertEquals(RATE, view.quote().points());
        verifyNoInteractions(charges);
        var confirmation = new VideoRemakeService.Confirm("新标题",
                List.of(new VideoRemakeService.ShotEdit("镜头缓慢从杯口移到杯身的把手", "这是新台词", "新字幕")), true, RATE);
        assertEquals("CONFIRMING", service.confirm("tenant", "actor", ID, confirmation).phase());
        assertEquals("CONFIRMING", service.confirm("tenant", "actor", ID, confirmation).phase());

        verify(charges).hold("tenant", "remake:" + ID, "binding", "actor", Map.of("video", 1));
        verify(store).queueConfirmation(eq(review), contains("这是新台词"));
        verify(charges, never()).finish(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"720,1280,9,16,144,remake_720p_second", "1920,1080,17,24,408,remake_1080p_second"})
    void analysisQuotesMeasuredOutputAndConfirmationReservesThatAmount(int width, int height, int duration,
            long rate, long points, String kind) throws Exception {
        var review = row("REVIEW", "{\"follow_source\":true}");
        var expected = BigDecimal.valueOf(points);
        var units = Map.of(kind, duration);
        when(store.find("tenant", ID, false)).thenReturn(Optional.of(review), Optional.of(review), Optional.of(review),
                Optional.of(row("CONFIRMING")));
        when(store.find("tenant", ID, true)).thenReturn(Optional.of(review));
        when(engine.remake("GET", TASK_PATH + "?store_id=tenant", null)).thenReturn(json.readTree("""
                {"id":"%s","store_id":"tenant","status":"awaiting_confirmation",
                 "output_spec":{"width":%s,"height":%s,"duration_seconds":%s},"plan":{
                   "title":"原分镜","shots":[{"duration":%s},{"duration":%s}]}}
                """.formatted(ENGINE, width, height, duration, duration / 2, duration - duration / 2)));
        when(charges.quote("tenant", "remake:" + ID)).thenReturn(
                new GenerationCharges.Quote(null, units, Map.of(kind, BigDecimal.valueOf(rate))));

        var quote = service.get("tenant", "actor", ID).quote();
        assertEquals(expected, quote.points());
        assertEquals(BigDecimal.valueOf(rate), quote.pointsPerSecond());
        var shots = List.of(new VideoRemakeService.ShotEdit("镜头缓慢从杯口移到杯身的把手", "不锈钢杯身", "杯身细节"),
                new VideoRemakeService.ShotEdit("镜头转向桌面的水杯并拉近", "水杯细节", "水杯"));
        assertThrows(BizException.class, () -> service.confirm("tenant", "actor", ID,
                new VideoRemakeService.Confirm("成片", shots, true, RATE)));
        verifyNoInteractions(charges);
        var result = service.confirm("tenant", "actor", ID, new VideoRemakeService.Confirm("成片", shots, true, expected));
        assertEquals("CONFIRMING", result.phase());
        verify(charges).hold("tenant", "remake:" + ID, "binding", "actor", units);
    }

    @Test
    void sourceMatchingRequiresMeasuredSpecificationBeforeCharging() {
        var review = row("REVIEW", "{\"follow_source\":true}");
        when(store.find("tenant", ID, false)).thenReturn(Optional.of(review));
        when(engine.remake("GET", TASK_PATH + "?store_id=tenant", null)).thenReturn(json.createObjectNode()
                .put("id", ENGINE).put("store_id", "tenant"));
        assertNull(service.get("tenant", "actor", ID).quote());
        assertThrows(BizException.class, () -> service.confirm("tenant", "actor", ID,
                new VideoRemakeService.Confirm("成片", List.of(), true, RATE)));
        verifyNoInteractions(charges);
    }

    @Test
    void successfulDeliverySettlesTheWholeReservation() throws Exception {
        var generating = row("GENERATING", "{\"follow_source\":true}");
        when(store.due()).thenReturn(List.of(generating));
        when(store.claim(eq(generating), anyString())).thenReturn(true);
        when(store.update(eq(generating), anyString(), eq("SUCCEEDED"), eq(ENGINE), isNull(), eq(0L))).thenReturn(true);
        when(engine.remake("GET", TASK_PATH + "?store_id=tenant", null)).thenReturn(json.readTree("""
                {"id":"%s","store_id":"tenant","status":"succeeded","delivery":{"typography_passed":true,
                 "video_url":"https://example.com/clip.mp4","cover_url":"https://example.com/cover.png"}}
                """.formatted(ENGINE)));
        service.advance();
        verify(charges).finishAll("tenant", "remake:" + ID);
        verify(charges, never()).finish(any(), any(), any());
    }

    @Test
    void klingUsesTheCurrentActorsUploadedPortrait() throws Exception {
        String reference = "rpa_" + "1".repeat(32);
        String portrait = "rpa_" + "2".repeat(32);
        var request = new VideoRemakeService.Start(ID, "binding", "douyin", "123", List.of("不锈钢"),
                null, reference, null, null, true, true, 0, portrait, List.of(" 请按说明清洗 "), true);
        var product = new Product("p", "123", "水杯", null, null, "https://example.com/product.png",
                "家居>水杯", null, List.of(), List.of());
        when(catalog.restorable(new TenantId("tenant"), "binding", "douyin", List.of("123")))
                .thenReturn(List.of(product));
        when(store.ownsAsset("tenant", "actor", "binding", reference, "video")).thenReturn(true);
        when(engine.remake("GET", "/capabilities", null)).thenReturn(json.createObjectNode().put("source_matching_available", true));
        assertThrows(BizException.class, () -> service.create("tenant", "actor", request));
        verify(store, never()).create(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());

        when(store.ownsAsset("tenant", "actor", "binding", portrait, "image")).thenReturn(true);
        when(store.find("tenant", ID, false)).thenReturn(Optional.empty(), Optional.empty(), Optional.of(row("CREATING")));
        service.create("tenant", "actor", request);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(store).create(eq("tenant"), eq(ID), eq("actor"), eq("binding"), eq("水杯"), payload.capture());
        var engineInput = json.readTree(payload.getValue());
        assertEquals(portrait, engineInput.at("/kling_portrait/image_asset_id").asText());
        assertTrue(engineInput.at("/kling_portrait/rights_confirmed").asBoolean());
        assertTrue(engineInput.at("/kling_portrait/adult_confirmed").asBoolean());
        assertTrue(engineInput.path("require_plan_confirmation").asBoolean());
        assertTrue(engineInput.path("follow_source").asBoolean());
        assertEquals("source", engineInput.path("orientation").asText());
        assertFalse(engineInput.has("duration_seconds"));
        assertFalse(engineInput.has("portrait"));
        assertEquals("家居>水杯", engineInput.at("/product/category").asText());
        assertEquals("请按说明清洗", engineInput.at("/ad_notices/0").asText());
    }

    @Test
    void definitiveFailureReleasesReservationButUncertainReceiptDoesNot() throws Exception {
        var generating = row("GENERATING");
        when(store.due()).thenReturn(List.of(generating));
        when(store.claim(eq(generating), anyString())).thenReturn(true);
        when(store.update(eq(generating), anyString(), anyString(), eq(ENGINE), any(), anyLong())).thenReturn(true);
        when(engine.remake("GET", TASK_PATH + "?store_id=tenant", null)).thenReturn(
                json.readTree("{\"id\":\"" + ENGINE + "\",\"store_id\":\"tenant\",\"status\":\"needs_attention\"}"),
                json.readTree("{\"id\":\"" + ENGINE + "\",\"store_id\":\"tenant\",\"status\":\"failed\"}"));

        service.advance();
        verify(charges, never()).finish(any(), any(), any());
        service.advance();
        verify(charges).finish("tenant", "remake:" + ID, Map.of());
    }

    private Task row(String phase) {
        return row(phase, "{}");
    }

    private Task row(String phase, String request) {
        return new Task("tenant", ID, "actor", "binding", "水杯", ENGINE, phase, request, "{}",
                null, null, null, Instant.EPOCH);
    }
}
