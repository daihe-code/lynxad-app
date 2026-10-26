package com.lynx.ad.modules.delivery.application;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.domain.model.InboxResult;
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
        String outcome=produced==0?"FAILED":state.regeneration!=null?"NEW_VERSION":complete?"GENERATED":"PARTIAL";
        String title=switch(outcome){case "FAILED"->"素材生成失败";case "NEW_VERSION"->"新版本已生成，待确认";case "PARTIAL"->"部分素材生成完成";default->"素材生成完成";};
        String description=switch(outcome){case "FAILED"->"本次未生成素材，原有素材保持不变。请查看失败原因。";case "NEW_VERSION"->"新素材已生成，请预览后确认使用。";case "PARTIAL"->"已生成 "+produced+" 条素材，部分任务未完成。可查看结果和失败原因。";default->"已生成 "+produced+" 条素材，可查看结果及登记、检查和同步状态。";};
        String body="计划「"+state.name+"」\n"+description;
        var revision=state.regeneration;
        String materialId=revision==null?null:revision.resultMaterialId;
        String materialType=revision==null?null:state.materials.stream().filter(m->m.id.equals(revision.sourceId)).map(m->m.type).findFirst().orElse(null);
        var result=new InboxResult(outcome,state.name,state.planId,state.engineJobId,operation,materialId,materialType,produced);
        String key=eventKey("creation-generated:"+state.planId+":"+state.engineJobId+":"+operation+":"+state.engineTerminalStatus);
        outbox.enqueue(new TenantId(state.tenantId),key,title,body,new BizRef(state.publishedEdit==null?"CREATION_GENERATED":"CREATION_EDIT_GENERATED",state.publishedEdit==null?state.planId:state.publishedEdit.targetPlanId,result),Instant.now());
    }
    private static String eventKey(String identity) {
        // Keep the complete outcome identity for deduplication within event_key VARCHAR(64).
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException("SHA-256 unavailable",unavailable);}
    }
}
