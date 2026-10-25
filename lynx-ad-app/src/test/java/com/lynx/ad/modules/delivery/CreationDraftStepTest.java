package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.math.BigDecimal;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;

class CreationDraftStepTest {
    final CreationStore store=mock(CreationStore.class);
    final ObjectMapper json=new ObjectMapper();
    final CreationState state=new CreationState();
    CreationWorkflowService service;

    @BeforeEach void setup() {
        state.planId="plan";state.tenantId="tenant";state.name="草稿计划";state.step=3;
        state.revision=7;state.packageVersion=3;state.confirmedVersion=3;
        state.analysisStatus="SUCCEEDED";state.budget=new BigDecimal("300");state.roi2Goal=new BigDecimal("1.5");
        state.products.add(new CreationState.Product("123","123","商品",new BigDecimal("50"),null,"https://assets.test/product.jpg","类目",10L,java.util.List.of(),java.util.List.of()));
        var material=new CreationState.Material();material.id="material";material.status="READY";state.materials.add(material);
        when(store.mutate(eq("tenant"),eq("plan"),any(),any())).thenAnswer(call->{
            assertEquals(state.revision,(Integer)call.getArgument(2));
            Consumer<CreationState> update=call.getArgument(3);update.accept(state);return state;
        });
        service=new CreationWorkflowService(store,null,null,null,null,json,null,null,null,null,
                mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
    }

    @ParameterizedTest @ValueSource(ints={1,2,3})
    void resumesSavedPageAfterReloadWithoutChangingMaterials(int step) throws Exception {
        state.step=step==3?2:3;
        var saved=service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,step));
        var reloaded=json.readValue(json.writeValueAsString(saved),CreationState.class);
        assertEquals(step,CreationView.of(reloaded).step());
        assertEquals(3,CreationView.of(reloaded).reachedStep());
        assertEquals(8,reloaded.revision);assertEquals(3,reloaded.packageVersion);assertEquals(3,reloaded.confirmedVersion);
        assertEquals("material",reloaded.materials.getFirst().id);assertEquals("READY",reloaded.materials.getFirst().status);
    }

    @Test void savingCurrentPageIsIdempotent() {
        service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,3));
        assertEquals(7,state.revision);assertEquals(3,state.confirmedVersion);
    }

    @Test void visitedGoalRemainsLitAfterReturningToProductsAndReloading() throws Exception {
        state.step=2;state.reachedStep=2;state.budget=null;state.materials.clear();
        var saved=service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,1));
        var reloaded=json.readValue(json.writeValueAsString(saved),CreationState.class);
        assertEquals(1,reloaded.step);assertEquals(2,CreationView.of(reloaded).reachedStep());
    }

    @Test void revisitingMaterialsAfterGoalEditConfirmsProgressEvenIfPageAlreadyMatches() {
        state.reachedStep=2;
        service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,3));
        assertEquals(3,state.reachedStep());assertEquals(8,state.revision);
        assertEquals(3,state.packageVersion);assertEquals(3,state.confirmedVersion);
    }

    @Test void legacyPublishedPlanKeepsAllStepsLitEvenWhenViewingProducts() {
        state.step=1;state.qcPlanId="123";
        assertEquals(4,CreationView.of(state).reachedStep());
    }

    @ParameterizedTest @ValueSource(ints={0,4,-1})
    void refusesInvalidStepsAndPublishPage(int step) {
        assertThrows(BizException.class,()->service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,step)));
        verifyNoInteractions(store);
    }

    @Test void missingPageDoesNotRestartTheDraft() {
        assertThrows(BizException.class,()->service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,null)));
        verifyNoInteractions(store);
    }

    @Test void allowsGoalPageWhileAiIsRunningWhenBasicChecksPass() {
        state.step=1;state.analysisStatus="RUNNING";
        service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,2));
        assertEquals(2,state.step);assertEquals(8,state.revision);assertEquals("RUNNING",state.analysisStatus);
    }

    @Test void invalidSelectionStillCannotEnterGoalPageAfterAiSucceeded() {
        state.step=1;state.products.clear();
        assertThrows(BizException.class,()->service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,2)));
        assertEquals(1,state.step);assertEquals(7,state.revision);
    }

    @Test void requiresValidSettingsBeforeEnteringMaterialsPage() {
        state.step=2;state.budget=null;
        assertThrows(BizException.class,()->service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,3)));
        assertEquals(2,state.step);assertEquals(7,state.revision);
    }

    @ParameterizedTest @ValueSource(strings={"QUEUED","SENDING","UNCERTAIN","CREATED"})
    void refusesNavigationAfterPublishingStarts(String status) {
        state.publishStatus=status;
        assertThrows(BizException.class,()->service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,2)));
        assertEquals(3,state.step);assertEquals(7,state.revision);
    }

    @Test void remotePlansCannotChangeDraftPosition() {
        state.qcPlanId="123";
        assertThrows(BizException.class,()->service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,2)));
        assertEquals(3,state.step);
    }

    @Test void backgroundWorkDoesNotPreventSavingCurrentEditingPage() {
        state.generationStatus="RUNNING";
        service.step(new TenantId("tenant"),"plan",new CreationWorkflowService.Step(7,2));
        assertEquals(2,state.step);assertEquals("RUNNING",state.generationStatus);assertEquals(3,state.confirmedVersion);
    }
}
