package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;

class CreationTypographyTest {
    @ParameterizedTest @ValueSource(strings={"pending","complete"})
    void coverRepairKeepsPlayableVideoAndOldSelectionUntilNewImageIsImported(String typographyStatus) throws Exception {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);var mapper=new ObjectMapper();
        var s=new CreationState();s.tenantId="tenant";s.planId="plan";s.enginePlanId="task";s.generationStatus="SUCCEEDED";
        var m=new CreationState.Material();m.id="old";m.productId="product";m.type="VIDEO";m.status="READY";
        m.engineMaterialId="engine-old";m.engineJobId="job";m.groupId="group-1";m.typographyStatus=typographyStatus;
        m.videoAssetId="playable-video";m.coverAssetId="old-cover";m.textAssetId="original-title";s.materials.add(m);
        when(store.get("tenant","plan")).thenReturn(s);
        when(store.mutate(eq("tenant"),eq("plan"),eq(1),any())).thenAnswer(call->{Consumer<CreationState> change=call.getArgument(3);change.accept(s);return s;});
        when(engine.post(endsWith("/engine-old/typography"),any())).thenReturn(mapper.readTree("""
            {"material_id":"new-version","parent_material_id":"engine-old","job_id":"job","status":"succeeded",
             "typography_status":"complete","delivery_mode":"full","urls":{"cover_url":"https://oss.test/new.png"}}
            """));
        var service=new CreationWorkflowService(store,null,null,null,engine,mapper,null,null,null,null,
                mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
        service.repairTypography(new TenantId("tenant"),"plan","old",new CreationWorkflowService.Version(1));
        assertEquals(2,s.materials.size());assertEquals("old-cover",m.coverAssetId);assertEquals("READY",m.status);
        var next=s.materials.get(1);assertEquals("old",next.replacesId);assertEquals("playable-video",next.videoAssetId);
        assertEquals("playable-video",next.importedAssets.get("VIDEO"));assertEquals(Set.of("COVER"),next.sourceUrls.keySet());
        assertEquals("original-title",next.textAssetId);assertEquals("complete",next.typographyStatus);
        assertEquals("IMPORTING",s.generationStatus);assertNull(s.confirmedVersion);assertEquals(2,s.packageVersion);
        var body=org.mockito.ArgumentCaptor.forClass(Object.class);verify(engine).post(anyString(),body.capture());
        assertFalse(((Map<?,?>)body.getValue()).containsKey("operation"));
        assertEquals("tenant",((Map<?,?>)body.getValue()).get("store_id"));
    }
    @Test void staleDraftCannotSubmitTypographyWork() {
        var store=mock(CreationStore.class);var engine=mock(CreationEngineClient.class);var s=new CreationState();s.revision=2;
        when(store.get("tenant","plan")).thenReturn(s);
        var service=new CreationWorkflowService(store,null,null,null,engine,new ObjectMapper(),null,null,null,null,
                mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
        assertThrows(BizException.class,()->service.repairTypography(new TenantId("tenant"),"plan","old",new CreationWorkflowService.Version(1)));
        verifyNoInteractions(engine);
    }
}
