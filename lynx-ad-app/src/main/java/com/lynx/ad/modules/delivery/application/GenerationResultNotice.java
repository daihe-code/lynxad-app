package com.lynx.ad.modules.delivery.application;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Enqueued under the creation row lock; delivery cannot replay generation or platform uploads. */
@Component
public class GenerationResultNotice {
    private final InboxOutboxStore outbox;
    public GenerationResultNotice(InboxOutboxStore outbox){this.outbox=outbox;}
    public void generated(CreationState state) {
        if(state.engineJobId==null||state.engineTerminalStatus==null)return;
        String operation=state.generationRetry!=null?state.generationRetry.requestId:state.regeneration!=null?state.regeneration.requestId:"batch";
        int produced=state.generationCounts.stream().mapToInt(CreationState.GenerationCount::completed).sum();
        boolean complete="succeeded".equals(state.engineTerminalStatus);
        String title=complete?"素材生成阶段已完成":"部分素材生成未完成";
        String body="计划「"+state.name+"」生成阶段已结束，已产出 "+produced+" 条素材。"+(complete?"":"成功结果已保留，可重试失败项。")+"素材登记、检查和千川同步状态请返回本计划查看。";
        String key=eventKey("creation-generated:"+state.planId+":"+state.engineJobId+":"+operation+":"+state.engineTerminalStatus);
        outbox.enqueue(new TenantId(state.tenantId),key,title,body,new BizRef("CREATION_GENERATED",state.planId),Instant.now());
    }
    private static String eventKey(String identity) {
        // Keep the complete outcome identity for deduplication within event_key VARCHAR(64).
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 unavailable",unavailable);}
    }
}
