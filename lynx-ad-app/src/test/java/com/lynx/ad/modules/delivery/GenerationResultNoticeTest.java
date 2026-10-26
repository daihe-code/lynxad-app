package com.lynx.ad.modules.delivery;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.application.GenerationResultNotice;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GenerationResultNoticeTest {
    @Test void noticeDistinguishesGenerationFromSyncAndUsesStableOutcomeIdentity(){
        var outbox=mock(InboxOutboxStore.class);var notice=new GenerationResultNotice(outbox);
        var s=new CreationState();s.planId="plan";s.tenantId="tenant";s.name="测试计划";s.engineJobId="job";s.engineTerminalStatus="partial_failed";
        s.generationCounts.add(new CreationState.GenerationCount("VIDEO",1,2,1));
        notice.generated(s);notice.generated(s);
        verify(outbox,times(2)).enqueue(eq(new TenantId("tenant")),anyString(),eq("部分素材生成完成"),contains("已生成 1 条素材"),argThat(ref->ref.bizType().equals("CREATION_GENERATED")&&ref.bizId().equals("plan")&&ref.result().jobId().equals("job")),any());
        s.generationRetry=new CreationState.GenerationRetry();s.generationRetry.requestId="retry-"+"x".repeat(100);notice.generated(s);
        s.generationRetry.requestId+="2";notice.generated(s);
        s.engineTerminalStatus="succeeded";notice.generated(s);
        s.engineJobId="another-job";notice.generated(s);
        var keys=ArgumentCaptor.forClass(String.class);
        verify(outbox,times(6)).enqueue(any(),keys.capture(),anyString(),anyString(),any(),any());
        var values=keys.getAllValues();
        assertTrue(values.stream().allMatch(k->k.matches("[0-9a-f]{64}")));
        assertEquals(values.get(0),values.get(1));assertEquals(5,new HashSet<>(values).size());
    }
    @Test void zeroOutputsAreFailureEvenWhenEngineSummarySaysSucceeded() {
        var outbox=mock(InboxOutboxStore.class);var notice=new GenerationResultNotice(outbox);
        var s=new CreationState();s.planId="plan";s.tenantId="tenant";s.name="零产出计划";s.engineJobId="job";
        for(String terminal:java.util.List.of("failed","partial_failed","succeeded","cancelled")) {
            s.engineTerminalStatus=terminal;notice.generated(s);
        }
        verify(outbox,times(4)).enqueue(any(),anyString(),eq("素材生成失败"),contains("本次未生成素材"),argThat(ref->ref.result().outcome().equals("FAILED")&&ref.result().produced()==0),any());
    }
    @Test void newVersionCarriesWorkingDraftAndExactMaterialWithoutPromisingReplacementChoice() {
        var outbox=mock(InboxOutboxStore.class);var notice=new GenerationResultNotice(outbox);
        var s=new CreationState();s.planId="working-draft";s.tenantId="tenant";s.name="原计划";s.engineJobId="revision-job";s.engineTerminalStatus="succeeded";
        s.generationCounts.add(new CreationState.GenerationCount("VIDEO",1,1));
        s.regeneration=new CreationState.Regeneration();s.regeneration.requestId="revision-request";s.regeneration.resultMaterialId="new-video";s.regeneration.sourceId="old-video";
        s.publishedEdit=new CreationState.PublishedEdit();s.publishedEdit.targetPlanId="published-plan";
        var old=new CreationState.Material();old.id="old-video";old.type="VIDEO";s.materials.add(old);
        notice.generated(s);
        var ref=ArgumentCaptor.forClass(BizRef.class);
        verify(outbox).enqueue(any(),anyString(),eq("新版本已生成，待确认"),contains("请预览后确认使用"),ref.capture(),any());
        assertEquals("published-plan",ref.getValue().bizId());
        var result=ref.getValue().result();assertEquals("working-draft",result.creationPlanId());assertEquals("new-video",result.materialId());assertEquals("VIDEO",result.materialType());assertEquals("revision-request",result.operationId());
    }
}
