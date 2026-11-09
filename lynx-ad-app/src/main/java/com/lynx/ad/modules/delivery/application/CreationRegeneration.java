package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.creative.infrastructure.adapter.CreationEngineClient;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import com.lynx.ad.modules.delivery.infrastructure.persistence.CreationStore;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One durable dispatch; all recovery uses revision metadata GETs, never a repeated POST. */
final class CreationRegeneration {
    private final CreationStore store;private final CreationEngineClient engine;
    private com.lynx.ad.modules.billing.application.GenerationCharges charges;
    void generationCharges(com.lynx.ad.modules.billing.application.GenerationCharges value){charges=value;}
    private void release(CreationState s){if(charges!=null&&s.generationChargeKey!=null){charges.finish(s.tenantId,s.generationChargeKey,Map.of());s.generationChargeKey=null;s.generationChargeBaseline=new ArrayList<>();}}
    CreationRegeneration(CreationStore store,CreationEngineClient engine){this.store=store;this.engine=engine;}
    private static boolean owns(CreationState s,String request){return s.regeneration!=null&&request.equals(s.regeneration.requestId)&&"REVISING".equals(s.generationStatus);}
    private void update(CreationState s,String request,Consumer<CreationState> change){store.mutate(s.tenantId,s.planId,null,current->{if(owns(current,request))change.accept(current);});}
    CreationState submit(CreationState snapshot) {
        var r=snapshot.regeneration;String request=r.requestId;var claimed=new AtomicBoolean();
        update(snapshot,request,s->{if("RESERVED".equals(s.regeneration.status)){s.regeneration.status="SENDING";s.regeneration.sentAt=System.currentTimeMillis();claimed.set(true);}});
        if(!claimed.get())return store.get(snapshot.tenantId,snapshot.planId);
        JsonNode response;
        try {
            if(charges!=null)charges.validate(snapshot.tenantId,snapshot.generationChargeKey);
            response=engine.post("/api/v1/capabilities/generation/materials/"+CreationEngineClient.query(r.parentId)+"/revisions",
                Map.of("job_id",r.jobId,"ai_plan_task_id",r.planTaskId,"store_id",snapshot.tenantId,"group_id",r.groupId,"operation","regenerate","target",r.target,"instruction",r.instruction));
            var receipt=response.path("revision");
            String id=response.path("revision_id").asText(),execution=response.path("execution_job_id").asText();
            if(!identifier(id)||!identifier(execution)||!r.jobId.equals(response.path("job_id").asText())||!r.parentId.equals(response.path("parent_material_id").asText())
                ||!r.planTaskId.equals(response.path("ai_plan_task_id").asText())||!r.groupId.equals(response.path("group_id").asText())
                ||!id.equals(receipt.path("id").asText())||!execution.equals(receipt.path("execution_job_id").asText())||!matches(snapshot,r,receipt))
                throw new BizException(8003,"重新生成回执不完整或归属不匹配，请核查引擎修订记录，原素材已保留");
        }catch(BizException e) {
            boolean uncertain=e.code()==8003;
            update(snapshot,request,s->{s.regeneration.status=uncertain?"UNCERTAIN":"FAILED";s.generationStatus=uncertain?"REVISING":"FAILED";s.generationMessage=uncertain?"重新生成提交结果待确认，原素材已保留，不会重复提交":"引擎未接受本次重新生成，原素材已保留，请核对原因后重试";if(!uncertain){s.generationIssues=new ArrayList<>(GenerationIssueMapper.requested(s,e.getMessage()));release(s);}});
            if(!uncertain)throw e;
            return store.get(snapshot.tenantId,snapshot.planId);
        }
        // Keep receipt persistence outside the request catch: local failure never becomes a safe rejection.
        String id=response.path("revision_id").asText(),execution=response.path("execution_job_id").asText();
        update(snapshot,request,s->{s.regeneration.revisionId=id;s.regeneration.executionJobId=execution;s.regeneration.status="CONFIRMED";s.generationMessage="重新生成已受理，正在等待本次修订结果；原素材仍可用";});
        return store.get(snapshot.tenantId,snapshot.planId);
    }
    /** Returns true only after this exact revision reports its new material ID. */
    boolean refresh(CreationState snapshot) {
        var r=snapshot.regeneration;String request=r.requestId;
        if("RESERVED".equals(r.status)){submit(snapshot);return false;}
        if(!identifier(r.revisionId)||!identifier(r.executionJobId)) {
            // The foreground call can still be in flight (client timeout: 240 seconds).
            if("SENDING".equals(r.status)&&r.sentAt>0&&System.currentTimeMillis()-r.sentAt<300_000)return false;
            update(snapshot,request,s->{s.regeneration.status="UNCERTAIN";s.generationMessage="缺少本次修订回执，结果待确认；原素材已保留，不会把旧任务当作新结果或重复提交";});return false;
        }
        JsonNode response;
        try{response=engine.get("/api/v1/capabilities/generation/materials/"+CreationEngineClient.query(r.parentId)+"/revisions?store_id="+CreationEngineClient.query(snapshot.tenantId));}
        catch(BizException unavailable){update(snapshot,request,s->s.generationMessage="本次修订状态暂未更新，原素材已保留，稍后继续只读核查");return false;}
        var rows=response.path("items");
        var candidates=new ArrayList<JsonNode>();
        if(r.parentId.equals(response.path("material_id").asText())&&rows.isArray()&&rows.size()<=500)for(var row:rows)if(r.revisionId.equals(row.path("id").asText()))candidates.add(row);
        if(candidates.size()!=1||!matches(snapshot,r,candidates.getFirst())||!r.executionJobId.equals(candidates.getFirst().path("execution_job_id").asText())) {
            update(snapshot,request,s->s.generationMessage="本次修订回执暂不能核实，原素材已保留，不会采用其他修订或重复提交");return false;
        }
        var row=candidates.getFirst();String status=row.path("status").asText();
        if(Set.of("failed","cancelled").contains(status)) {
            update(snapshot,request,s->{s.regeneration.status="FAILED";s.generationStatus="FAILED";s.generationMessage="本次重新生成未完成，原素材已保留，可调整要求后重试";s.generationIssues=new ArrayList<>(GenerationIssueMapper.requested(s,GenerationIssueMapper.message(row.path("error"),s.generationMessage)));s.progress=0;release(s);});return false;
        }
        if(!"succeeded".equals(status)) {
            update(snapshot,request,s->s.generationMessage="本次修订"+("paused".equals(status)?"已暂停":"仍在生成中")+"，原素材已保留");return false;
        }
        String material=row.path("material_id").asText();
        if(!identifier(material)||r.parentId.equals(material)){update(snapshot,request,s->s.generationMessage="修订尚未返回新的素材标识，继续保留原素材");return false;}
        var accepted=new AtomicBoolean();
        update(snapshot,request,s->{s.regeneration.resultMaterialId=material;s.regeneration.status="SUCCEEDED";s.generationStatus="RUNNING";s.generationMessage="本次修订已生成，正在登记新素材并同步千川";accepted.set(true);});
        return accepted.get();
    }
    static boolean outputMatches(CreationState s,JsonNode job) {
        var r=s.regeneration;if(r==null||!"SUCCEEDED".equals(r.status))return true;
        if(!identifier(r.resultMaterialId))return false;
        if(!s.tenantId.equals(job.path("store_id").asText())||!r.planTaskId.equals(job.path("ai_plan_task_id").asText()))return false;
        for(var group:job.path("groups"))if(r.groupId.equals(group.path("group_id").asText()))for(var item:group.path("materials"))
            if(r.resultMaterialId.equals(item.path("material_id").asText())&&r.target.equals(item.path("material_type").asText())&&"succeeded".equals(item.path("status").asText())&&"v1".equals(item.path("version").asText())&&r.parentId.equals(item.path("detail").path("parent_material_id").asText()))return true;
        return false;
    }
    private static boolean matches(CreationState s,CreationState.Regeneration r,JsonNode row) {
        return s.tenantId.equals(row.path("store_id").asText())&&r.parentId.equals(row.path("parent_material_id").asText())&&r.jobId.equals(row.path("generation_job_id").asText())
            &&r.planTaskId.equals(row.path("ai_plan_task_id").asText())&&r.groupId.equals(row.path("group_id").asText())&&r.target.equals(row.path("target").asText())
            &&"regenerate".equals(row.path("operation").asText())&&r.instruction.equals(row.path("instruction").asText());
    }
    private static boolean identifier(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{1,128}");}
}
