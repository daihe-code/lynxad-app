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
    @ParameterizedTest @ValueSource(strings={"QUEUED","SUBMITTING","RUNNING","IMPORTING","REVISING","UNCERTAIN"})
    void generatingDraftsAndPublishedEditDraftsRejectSelectionAtTheSaveBoundary(String status) throws Exception {
        when(qc.selected(tenant,"account","aweme",List.of("456"))).thenReturn(List.of(ProductBasicAnalysisTest.valid("456")));
        for(boolean published:List.of(false,true)) {
            saved.get().generationStatus=status;saved.get().engineJobId="active-job";
            if(published){saved.get().publishedEdit=new CreationState.PublishedEdit();saved.get().publishedEdit.status="DRAFT";}
            String before=json.writeValueAsString(saved.get());
            var error=assertThrows(BizException.class,()->service.select(tenant,new CreationWorkflowService.Selection(id,saved.get().revision,"account","aweme",List.of("456"))));
            assertEquals(1403,error.code());assertTrue(CreationView.of(saved.get()).productSelectionLocked());
            assertEquals(before,json.writeValueAsString(saved.get()));
        }
        verify(plans,never()).save(any());verifyNoInteractions(engine);
    }

    @Test void noGenerationChoiceSurvivesReloadWithoutStartingEngineOrInvalidatingMaterials() throws Exception {
        saved.get().step=3;saved.get().confirmedVersion=saved.get().packageVersion;
        var result=service.generate(tenant,id,new CreationWorkflowService.Generate(1,List.of()));
        var reloaded=json.readValue(json.writeValueAsString(result),CreationState.class);
        assertEquals(List.of(),CreationView.of(reloaded).generationSelection());
        assertEquals("NONE",reloaded.generationStatus);assertEquals(3,reloaded.step);
        assertEquals(2,reloaded.revision);assertEquals(1,reloaded.packageVersion);assertEquals(1,reloaded.confirmedVersion);
        verifyNoInteractions(engine);
    }

    @Test void generationChoiceRetainsSelectedProductsAndStillQueuesOnlyThoseProducts() {
        var result=service.generate(tenant,id,new CreationWorkflowService.Generate(1,List.of("123")));
        assertEquals(List.of("123"),result.generationSelection);
        assertEquals(List.of("123"),result.generationProductIds);assertEquals("QUEUED",result.generationStatus);
        assertEquals(List.of("123"),CreationView.of(copy(result)).generationSelection());
    }

    @Test void invalidGenerationChoiceCannotBeSaved() {
        assertThrows(BizException.class,()->service.generate(tenant,id,new CreationWorkflowService.Generate(1,List.of("unknown"))));
        assertNull(saved.get().generationSelection);assertEquals("NONE",saved.get().generationStatus);
        assertThrows(BizException.class,()->service.generate(tenant,id,new CreationWorkflowService.Generate(1,null)));
    }

    @Test void changedGoalInvalidatesLaterProgressUntilMaterialsAreRevisited() {
        saved.get().step=2;saved.get().reachedStep=3;
        var updated=service.goal(tenant,id,goal());
        assertEquals(2,CreationView.of(updated).reachedStep());
        service.step(tenant,id,new CreationWorkflowService.Step(updated.revision,2));
        assertEquals(2,CreationView.of(saved.get()).reachedStep());
        service.step(tenant,id,new CreationWorkflowService.Step(saved.get().revision,3));
        assertEquals(3,CreationView.of(saved.get()).reachedStep());
    }

    @Test void renamingOrReformattingTheSameGoalDoesNotInvalidateLaterProgress() {
        saved.get().step=2;saved.get().reachedStep=3;
        var updated=service.goal(tenant,id,new CreationWorkflowService.Goal(1,"仅修改计划名称","CUSTOM",new BigDecimal("300.00"),new BigDecimal("1.500"),"FROM_NOW",null,null));
        assertEquals(3,CreationView.of(updated).reachedStep());
    }

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
    @Test void keepCurrentCombinationPersistsAndRepeatedConfirmationDoesNotChangeRevision() {
        var result=service.keepProductGroup(tenant,id,new CreationWorkflowService.Version(saved.get().revision));
        assertEquals("KEEP_CURRENT",result.groupingDecision);
        assertEquals("KEEP_CURRENT",CreationView.of(copy(saved.get())).groupingDecision());
        int revision=result.revision;
        assertEquals(revision,service.keepProductGroup(tenant,id,new CreationWorkflowService.Version(revision)).revision);
        assertThrows(BizException.class,()->service.keepProductGroup(tenant,id,new CreationWorkflowService.Version(revision-1)));
        verifyNoInteractions(engine);
    }
    @Test void changedProductFactsInvalidateThePreviousCombinationConfirmation() {
        saved.get().groupingDecision="KEEP_CURRENT";saved.get().reachedStep=3;
        var product=saved.get().products.getFirst();
        var changed=new CreationState.Product(product.productId(),product.qcProductId(),product.name(),new BigDecimal("600"),
                product.userPrice(),product.imageUrl(),product.category(),product.stock(),product.tags(),product.unavailableReasons());
        when(qc.refreshPrices(eq(tenant),eq("account"),eq("aweme"),anyList())).thenReturn(List.of(changed));
        queueAnalysis();
        assertNull(saved.get().groupingDecision);assertEquals("QUEUED",saved.get().analysisStatus);
        assertEquals(1,saved.get().reachedStep());
        verifyNoInteractions(engine);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void selectionProgressIsResetOnlyWhenProductsReallyChange(boolean changed) {
        saved.get().reachedStep=3;
        var selected=changed?ProductBasicAnalysisTest.valid("456"):saved.get().products.getFirst();
        when(qc.selected(eq(tenant),eq("account"),eq("aweme"),anyList())).thenReturn(List.of(selected));
        var updated=service.select(tenant,new CreationWorkflowService.Selection(id,1,"account","aweme",List.of(selected.qcProductId())));
        assertEquals(changed?1:3,updated.reachedStep());
    }
    @ParameterizedTest @ValueSource(strings={"ValueError","TimeoutError","ModelRequestError"})
    void modelFallbackIsFailedEvenWhenTheEngineRequestReturnedSuccessfully(String error) {
        queueAnalysis();
        when(engine.post(eq("/api/v1/capabilities/perception/products/analyze"),any())).thenAnswer(call->{
            Map<String,Object> body=call.getArgument(1);
            var response=json.createObjectNode();response.set("product_set",json.createObjectNode());
            response.set("selection_analysis",json.createObjectNode().put("analysis_id",body.get("analysis_id").toString()).put("input_version",body.get("input_version").toString()));
            response.set("processing",json.createObjectNode().put("inference_error",error));return response;
        });
        service.process(copy(saved.get()));
        assertEquals("FAILED",saved.get().analysisStatus);assertNotNull(saved.get().analysis);
        assertTrue(saved.get().analysisError.contains("尚未生成有效结果"));
        assertTrue(CreationView.of(saved.get()).basicAnalysis().canContinue());
        service.step(tenant,id,new CreationWorkflowService.Step(saved.get().revision,2));
        assertTrue(saved.get().analysisConfirmation==null||saved.get().analysisConfirmation.isNull());
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
    @Test void structuredAnalysisCarriesAccountIdentityCompleteFactsAndIsConfirmedWithItsInputVersion() {
        queueAnalysis();String request=saved.get().analysisRequestId;
        String fingerprint=com.lynx.ad.modules.delivery.domain.service.ProductBasicAnalysisService.fingerprint(saved.get().products);
        when(engine.post(eq("/api/v1/capabilities/perception/products/analyze"),any())).thenAnswer(call->{
            Map<String,Object> body=call.getArgument(1);
            assertEquals("account",body.get("account_id"));assertEquals(request,body.get("analysis_id"));assertEquals(fingerprint,body.get("input_version"));
            Map<?,?> product=((List<Map<?,?>>)body.get("products")).getFirst();
            assertEquals("123",product.get("id"));assertNotNull(product.get("image_url"));assertNotNull(product.get("inventory"));assertNotNull(product.get("availability"));
            var report=json.createObjectNode().put("analysis_id",request).put("input_version",fingerprint);
            report.set("handoff",json.createObjectNode().set("material",json.createArrayNode()));
            var response=json.createObjectNode();response.set("selection_analysis",report);response.set("product_set",json.createObjectNode());return response;
        });
        service.process(copy(saved.get()));
        assertEquals("SUCCEEDED",saved.get().analysisStatus);assertNull(saved.get().analysisConfirmation);
        service.step(tenant,id,new CreationWorkflowService.Step(saved.get().revision,2));
        assertEquals(request,saved.get().analysisConfirmation.path("analysis_id").asText());
        assertEquals(fingerprint,saved.get().analysisConfirmation.path("input_version").asText());
        assertTrue(saved.get().analysisConfirmation.path("selection_confirmed").asBoolean());
        assertEquals("user",saved.get().analysisConfirmation.path("user_id").asText());
    }
    @Test void wrongEngineAnalysisVersionIsRejectedWithoutLosingSelectedProducts() {
        queueAnalysis();
        var response=json.createObjectNode();response.set("selection_analysis",json.createObjectNode().put("input_version","old").put("analysis_id","other"));response.set("product_set",json.createObjectNode());
        when(engine.post(eq("/api/v1/capabilities/perception/products/analyze"),any())).thenReturn(response);
        service.process(copy(saved.get()));
        assertEquals("FAILED",saved.get().analysisStatus);assertEquals("123",saved.get().products.getFirst().qcProductId());
        assertTrue(saved.get().analysis==null||saved.get().analysis.isNull());assertTrue(saved.get().basicAnalysis.canContinue());assertTrue(saved.get().analysisError.contains("版本"));
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
