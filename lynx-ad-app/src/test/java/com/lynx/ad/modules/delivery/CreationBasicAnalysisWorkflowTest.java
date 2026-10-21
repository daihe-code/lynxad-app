package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.api.dto.CreationView;
import com.lynx.ad.modules.delivery.application.CreationWorkflowService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.CreationQianchuanGateway;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;

class CreationBasicAnalysisWorkflowTest {
    final ObjectMapper json=new ObjectMapper();
    final CreationStore store=mock(CreationStore.class);
    final CreationEngineClient engine=mock(CreationEngineClient.class);
    final CreationQianchuanGateway qc=mock(CreationQianchuanGateway.class);
    final AdPlanRepository plans=mock(AdPlanRepository.class);
    final TenantId tenant=new TenantId("tenant");
    final AtomicReference<CreationState> saved=new AtomicReference<>();
    CreationWorkflowService service;
    String id;

    @BeforeEach void setup() {
        var plan=AdPlan.draft(tenant,new AccountId("account"),new AwemeId("aweme"),new PlanName("商品投放"),
                new DeliverySetting(SmartBidType.CUSTOM,Money.ofYuan(new BigDecimal("300")),Optional.of(new RoiGoal(new BigDecimal("1.5"))),Schedule.fromNow(),AudienceJson.empty()),
                List.of(new QcProductId("123")));
        id=plan.id().value();
        var s=new CreationState();s.planId=id;s.tenantId="tenant";s.userId="user";s.accountId="account";s.awemeId="aweme";s.name="商品投放";
        s.products.add(ProductBasicAnalysisTest.valid("123"));s.enginePlanId="task";
        s.budget=new BigDecimal("300");s.roi2Goal=new BigDecimal("1.5");
        saved.set(s);
        when(plans.findById(tenant,new PlanId(id))).thenReturn(Optional.of(plan));
        when(store.get("tenant",id)).thenAnswer(call->copy(saved.get()));
        when(store.mutate(eq("tenant"),eq(id),any(),any())).thenAnswer(call->{
            var current=copy(saved.get());Integer expected=call.getArgument(2);
            if(expected!=null&&expected!=current.revision)throw new BizException(1404,"草稿已更新");
            Consumer<CreationState> change=call.getArgument(3);change.accept(current);saved.set(current);return copy(current);
        });
        when(qc.refreshPrices(eq(tenant),eq("account"),eq("aweme"),anyList())).thenAnswer(call->call.getArgument(3));
        service=new CreationWorkflowService(store,plans,mock(AdPlanCreativeBindingStore.class),qc,engine,json,null,null,null,null,
                mock(BillingTrialService.class),mock(PlatformTransactionManager.class));
    }
    CreationState copy(CreationState state) { return json.convertValue(state,CreationState.class); }
    CreationWorkflowService.Goal goal() { return new CreationWorkflowService.Goal(saved.get().revision,"已填写目标","CUSTOM",new BigDecimal("600"),new BigDecimal("2.25"),"FROM_NOW",null,null); }
    void queueAnalysis() { service.analyze(tenant,id,new CreationWorkflowService.Analysis(saved.get().revision,false)); }

    @ParameterizedTest @ValueSource(strings={"QUEUED","RUNNING","FAILED","NONE"})
    void goalEditingDoesNotRequireEngineAvailabilityOrAiSuccess(String aiStatus) {
        saved.get().analysisStatus=aiStatus;saved.get().enginePlanId=null;
        var result=service.goal(tenant,id,goal());
        assertEquals(new BigDecimal("600"),result.budget);assertEquals(new BigDecimal("2.25"),result.roi2Goal);
        assertEquals("已填写目标",result.name);assertEquals(3,result.step);assertTrue(result.basicAnalysis.canContinue());
        verifyNoInteractions(engine);
    }
    @Test void analysisFailureKeepsBasicChecksAndAllowsNextStep() {
        queueAnalysis();
        when(engine.post(anyString(),any())).thenThrow(new BizException(8003,"引擎连接失败"));
        service.process(copy(saved.get()));
        assertEquals("FAILED",saved.get().analysisStatus);
        assertTrue(CreationView.of(saved.get()).basicAnalysis().canContinue());
        service.step(tenant,id,new CreationWorkflowService.Step(saved.get().revision,2));
        assertEquals(2,saved.get().step);
    }
    @Test void basicErrorsStillBlockTargetsEvenAfterAiSuccess() {
        saved.get().analysisStatus="SUCCEEDED";
        saved.get().products=List.of(new CreationState.Product("123","123","不可投商品",null,null,null,null,10L,List.of(),List.of("审核中")));
        assertThrows(BizException.class,()->service.goal(tenant,id,goal()));
        assertThrows(BizException.class,()->service.step(tenant,id,new CreationWorkflowService.Step(1,2)));
        verifyNoInteractions(engine);assertEquals(1,saved.get().step);
    }
    @Test void retryDuringAnActiveAnalysisKeepsItsDurableRequestIdentity() {
        queueAnalysis();String request=saved.get().analysisRequestId;int revision=saved.get().revision;
        service.analyze(tenant,id,new CreationWorkflowService.Analysis(revision,true));
        assertEquals(request,saved.get().analysisRequestId);assertEquals(revision,saved.get().revision);
        verifyNoInteractions(engine);
    }
    @Test void completedAnalysisDoesNotOverwriteGoalsEditedDuringTheRequest() {
        queueAnalysis();
        when(engine.post(eq("/api/v1/capabilities/perception/products/analyze"),any())).thenAnswer(call->{
            service.goal(tenant,id,goal());
            return json.readTree("{\"product_set\":{\"product_count\":1},\"dimensions\":{}}");
        });
        service.process(copy(saved.get()));
        assertEquals("SUCCEEDED",saved.get().analysisStatus);assertEquals(3,saved.get().revision);
        assertEquals("已填写目标",saved.get().name);assertEquals(new BigDecimal("600"),saved.get().budget);
        assertEquals(new BigDecimal("2.25"),saved.get().roi2Goal);assertEquals(3,saved.get().step);
    }
    void changeSelectionAndQueueNewAnalysis() {
        when(qc.selected(eq(tenant),eq("account"),eq("aweme"),eq(List.of("456")))).thenReturn(List.of(ProductBasicAnalysisTest.valid("456")));
        service.select(tenant,new CreationWorkflowService.Selection(id,saved.get().revision,"account","aweme",List.of("456")));
        queueAnalysis();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void oldSuccessAndFailureCannotOverwriteNewSelectionOrNewAnalysis(boolean failed) {
        queueAnalysis();String oldRequest=saved.get().analysisRequestId;
        when(engine.post(eq("/api/v1/capabilities/perception/products/analyze"),any())).thenAnswer(call->{
            changeSelectionAndQueueNewAnalysis();
            if(failed)throw new BizException(8003,"旧请求失败");
            return json.readTree("{\"product_set\":{\"marker\":\"OLD\"}}");
        });
        service.process(copy(saved.get()));
        assertEquals("456",saved.get().products.getFirst().qcProductId());
        assertNotEquals(oldRequest,saved.get().analysisRequestId);assertEquals("QUEUED",saved.get().analysisStatus);
        assertTrue(saved.get().analysis==null||saved.get().analysis.isNull());assertNull(saved.get().analysisError);assertNull(saved.get().enginePlanId);
    }
    @Test void lateEngineRegistrationCannotAttachAnOldTaskToNewProducts() throws Exception {
        saved.get().enginePlanId=null;queueAnalysis();
        when(engine.post(eq("/api/v1/plan-tasks"),any())).thenAnswer(call->{
            changeSelectionAndQueueNewAnalysis();return json.readTree("{\"ai_plan_task_id\":\"old-task\"}");
        });
        service.process(copy(saved.get()));
        assertNull(saved.get().enginePlanId);assertEquals("QUEUED",saved.get().analysisStatus);
        verify(engine,never()).post(eq("/api/v1/capabilities/perception/products/analyze"),any());
    }
    @Test void generationCanRegisterEngineTaskAfterAnalysisFailed() throws Exception {
        saved.get().analysisStatus="FAILED";saved.get().enginePlanId=null;
        service.generate(tenant,id,new CreationWorkflowService.Generate(1,List.of("123")));
        when(engine.post(eq("/api/v1/plan-tasks"),any())).thenReturn(json.readTree("{\"ai_plan_task_id\":\"generation-task\"}"));
        when(engine.post(eq("/api/v1/capabilities/perception/products/recommend-groups"),any())).thenReturn(json.readTree("{}"));
        when(engine.post(eq("/api/v1/capabilities/generation/jobs"),any())).thenReturn(json.readTree("{\"job_id\":\"job\"}"));
        service.process(copy(saved.get()));
        assertEquals("generation-task",saved.get().enginePlanId);assertEquals("RUNNING",saved.get().generationStatus);
        assertEquals("job",saved.get().engineJobId);assertEquals("FAILED",saved.get().analysisStatus);
        verify(engine,never()).post(eq("/api/v1/capabilities/perception/products/analyze"),any());
    }
    @Test void registrationFailureDoesNotPretendThatMediaSubmissionIsUncertain() {
        saved.get().enginePlanId=null;service.generate(tenant,id,new CreationWorkflowService.Generate(1,List.of("123")));
        when(engine.post(eq("/api/v1/plan-tasks"),any())).thenThrow(new BizException(8003,"初始化超时"));
        service.process(copy(saved.get()));
        assertEquals("FAILED",saved.get().generationStatus);assertTrue(saved.get().generationMessage.contains("尚未提交"));
        verify(engine,never()).post(eq("/api/v1/capabilities/generation/jobs"),any());
    }
    @Test void explicitGenerationTakesPriorityOverQueuedAdvisoryAnalysis() throws Exception {
        queueAnalysis();service.generate(tenant,id,new CreationWorkflowService.Generate(saved.get().revision,List.of("123")));
        when(engine.post(eq("/api/v1/capabilities/perception/products/recommend-groups"),any())).thenReturn(json.readTree("{}"));
        when(engine.post(eq("/api/v1/capabilities/generation/jobs"),any())).thenReturn(json.readTree("{\"job_id\":\"job\"}"));
        service.process(copy(saved.get()));
        assertEquals("RUNNING",saved.get().generationStatus);assertEquals("QUEUED",saved.get().analysisStatus);
        verify(engine,never()).post(eq("/api/v1/capabilities/perception/products/analyze"),any());
    }
    @Test void aPriceRefreshStartsANewAnalysisInsteadOfKeepingAnObsoleteRunningRequest() {
        queueAnalysis();String request=saved.get().analysisRequestId;
        var p=ProductBasicAnalysisTest.valid("123");
        var changed=new CreationState.Product(p.productId(),p.qcProductId(),p.name(),new BigDecimal("599"),null,p.imageUrl(),p.category(),p.stock(),p.tags(),p.unavailableReasons());
        when(qc.refreshPrices(eq(tenant),eq("account"),eq("aweme"),anyList())).thenReturn(List.of(changed));
        service.analyze(tenant,id,new CreationWorkflowService.Analysis(saved.get().revision,false));
        assertNotEquals(request,saved.get().analysisRequestId);assertEquals("QUEUED",saved.get().analysisStatus);
        assertEquals(new BigDecimal("599"),saved.get().products.getFirst().price());
    }
    @Test void addingOneFormatAlsoRegistersTheEngineWithoutRequiringAiAnalysis() throws Exception {
        saved.get().enginePlanId=null;saved.get().analysisStatus="FAILED";
        service.addition(tenant,id,new CreationWorkflowService.Addition(1,"123","IMAGE","通勤场景","one-card"));
        when(engine.post(eq("/api/v1/plan-tasks"),any())).thenReturn(json.readTree("{\"ai_plan_task_id\":\"registered\"}"));
        when(engine.post(eq("/api/v1/capabilities/generation/materials/additions"),any())).thenReturn(json.readTree("{\"request_id\":\"one-card\",\"product_id\":\"123\",\"target\":\"product_card\",\"ai_plan_task_id\":\"registered\",\"job_id\":\"one-job\"}"));
        service.process(copy(saved.get()));
        assertEquals("registered",saved.get().enginePlanId);assertEquals("RUNNING",saved.get().generationStatus);
        assertEquals("registered",saved.get().generationAdditions.getFirst().engineRequest.path("ai_plan_task_id").asText());
        assertEquals("one-job",saved.get().generationAdditions.getFirst().engineJobId);
        verify(engine,never()).post(eq("/api/v1/capabilities/perception/products/analyze"),any());
    }
}
