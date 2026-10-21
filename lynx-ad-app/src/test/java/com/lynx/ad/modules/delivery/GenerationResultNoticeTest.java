package com.lynx.ad.modules.delivery;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.application.GenerationResultNotice;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class GenerationResultNoticeTest {
    @Test void noticeDistinguishesGenerationFromSyncAndUsesStableOutcomeIdentity(){
        var outbox=mock(InboxOutboxStore.class);var notice=new GenerationResultNotice(outbox);
        var s=new CreationState();s.planId="plan";s.tenantId="tenant";s.name="测试计划";s.engineJobId="job";s.engineTerminalStatus="partial_failed";
        s.generationCounts.add(new CreationState.GenerationCount("VIDEO",1,2,1));
        notice.generated(s);notice.generated(s);
        verify(outbox,times(2)).enqueue(eq(new TenantId("tenant")),eq("creation-generated:plan:job:batch:partial_failed"),eq("部分素材生成未完成"),contains("已产出 1 条素材"),eq(new BizRef("CREATION_GENERATED","plan")),any());
        verify(outbox,times(2)).enqueue(any(),anyString(),anyString(),contains("千川同步状态请返回本计划查看"),any(),any());
        s.generationRetry=new CreationState.GenerationRetry();s.generationRetry.requestId="retry";notice.generated(s);
        verify(outbox).enqueue(any(),eq("creation-generated:plan:job:retry:partial_failed"),anyString(),anyString(),any(),any());
    }
}
