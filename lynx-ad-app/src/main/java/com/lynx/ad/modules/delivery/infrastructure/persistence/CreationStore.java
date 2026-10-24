package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Repository
public class CreationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public CreationStore(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.json=json; tx=new TransactionTemplate(manager);
    }
    public void insert(CreationState state) {
        jdbc.update("INSERT INTO delivery_creation(plan_id,tenant_id,revision,state_json,work_pending,updated_at) VALUES(?,?,?,?,?,?)",
                state.planId,state.tenantId,state.revision,encode(state),work(state),now());
    }
    public CreationState get(String tenant,String id) { return read(tenant,id,false); }
    public Optional<com.fasterxml.jackson.databind.JsonNode> publishedPayload(String tenant,String id) {
        return jdbc.query("SELECT payload_json FROM delivery_publish_attempt WHERE tenant_id=? AND plan_id=? AND status='CREATED' ORDER BY created_at DESC LIMIT 1",
                (r,n)->{try{return json.readTree(r.getString(1));}catch(Exception e){throw new IllegalStateException(e);}},tenant,id).stream().findFirst();
    }
    private CreationState read(String tenant,String id,boolean lock) {
        return jdbc.query("SELECT state_json FROM delivery_creation WHERE tenant_id=? AND plan_id=?"+(lock?" FOR UPDATE":""),
                (r,n)->decode(r.getString(1)),tenant,id).stream().findFirst().orElseThrow(()->new BizException(1403,"创建草稿不存在或不属于当前账户"));
    }
    public CreationState mutate(String tenant,String id,Integer expected,Consumer<CreationState> change) {
        return tx.execute(status->{
            var state=read(tenant,id,true);
            if(expected!=null && state.revision!=expected) throw new BizException(1404,"草稿已更新，请刷新后重试");
            change.accept(state);
            jdbc.update("UPDATE delivery_creation SET revision=?,state_json=?,work_pending=?,updated_at=? WHERE tenant_id=? AND plan_id=?",
                    state.revision,encode(state),work(state),now(),tenant,id);
            return state;
        });
    }
    public void delete(String tenant,String id) { jdbc.update("DELETE FROM delivery_creation WHERE tenant_id=? AND plan_id=?",tenant,id); }
    public List<CreationState> due() {
        return jdbc.query("SELECT state_json FROM delivery_creation WHERE work_pending=1 AND (lease_until IS NULL OR lease_until<?) ORDER BY updated_at LIMIT 100",
                (r,n)->decode(r.getString(1)),now());
    }
    public boolean claim(CreationState s) { return jdbc.update("UPDATE delivery_creation SET lease_until=? WHERE tenant_id=? AND plan_id=? AND (lease_until IS NULL OR lease_until<?)",
            Timestamp.from(Instant.now().plusSeconds(600)),s.tenantId,s.planId,now())==1; }
    public void heartbeat(CreationState s) { jdbc.update("UPDATE delivery_creation SET lease_until=? WHERE tenant_id=? AND plan_id=?", Timestamp.from(Instant.now().plusSeconds(600)),s.tenantId,s.planId); }
    public void release(CreationState s) { jdbc.update("UPDATE delivery_creation SET lease_until=? WHERE tenant_id=? AND plan_id=?",Timestamp.from(Instant.now().plusSeconds(s.needsGenerationReconciliation()?30:5)),s.tenantId,s.planId); }
    public record Attempt(String id,String tenant,String planId,int revision,String key,String hash,String payload,String status,String qcId,String error) {}
    public Optional<Attempt> attempt(String tenant,String key) {
        return jdbc.query("SELECT * FROM delivery_publish_attempt WHERE tenant_id=? AND request_key=?",(r,n)->new Attempt(r.getString("id"),tenant,r.getString("plan_id"),r.getInt("revision"),key,r.getString("payload_hash"),r.getString("payload_json"),r.getString("status"),r.getString("qc_plan_id"),r.getString("error_message")),tenant,key).stream().findFirst();
    }
    public void attempt(Attempt a) {
        jdbc.update("INSERT INTO delivery_publish_attempt(id,tenant_id,plan_id,revision,request_key,payload_hash,payload_json,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                a.id,a.tenant,a.planId,a.revision,a.key,a.hash,a.payload,a.status,now(),now());
    }
    public boolean claimPublish(String tenant,String key) { return jdbc.update("UPDATE delivery_publish_attempt SET status='SENDING',updated_at=? WHERE tenant_id=? AND request_key=? AND status='QUEUED'",now(),tenant,key)==1; }
    public void finishPublish(String tenant,String key,String state,String qcId,String error) { jdbc.update("UPDATE delivery_publish_attempt SET status=?,qc_plan_id=?,error_message=?,updated_at=? WHERE tenant_id=? AND request_key=? AND status<>'CREATED'",state,qcId,error,now(),tenant,key); }
    public List<Attempt> queued() { return jdbc.query("SELECT tenant_id,request_key FROM delivery_publish_attempt WHERE status='QUEUED' ORDER BY created_at LIMIT 10",(r,n)->attempt(r.getString(1),r.getString(2)).orElseThrow()); }
    public List<Attempt> staleSending() { return jdbc.query("SELECT tenant_id,request_key FROM delivery_publish_attempt WHERE status='SENDING' AND updated_at<?",(r,n)->attempt(r.getString(1),r.getString(2)).orElseThrow(),Timestamp.from(Instant.now().minusSeconds(300))); }
    public List<Attempt> receipts() { return jdbc.query("SELECT tenant_id,request_key FROM delivery_publish_attempt WHERE status='CREATED' AND applied=0 LIMIT 20",(r,n)->attempt(r.getString(1),r.getString(2)).orElseThrow()); }
    public void applied(Attempt a) { jdbc.update("UPDATE delivery_publish_attempt SET applied=1 WHERE tenant_id=? AND request_key=? AND status='CREATED'",a.tenant,a.key); }
    private static int work(CreationState s) {
        boolean missingRevisionReceipt=s.regeneration!=null&&"UNCERTAIN".equals(s.regeneration.status)
                &&(s.regeneration.revisionId==null||s.regeneration.executionJobId==null);
        return s.materialEditPending()||Set.of("QUEUED","RUNNING").contains(s.analysisStatus)||!missingRevisionReceipt&&Set.of("QUEUED","SUBMITTING","RUNNING","IMPORTING","REVISING","UNCERTAIN").contains(s.generationStatus)
                ||s.needsGenerationReconciliation()||s.materials.stream().anyMatch(m->"PROCESSING".equals(m.status)) ? 1 : 0;
    }
    public Set<String> usedMedia(String tenant) {
        Set<String> result=new HashSet<>();
        for(String body:jdbc.query("SELECT payload_json FROM delivery_publish_attempt WHERE tenant_id=? AND status='CREATED'",(r,n)->r.getString(1),tenant))try {
            for(var c:json.readTree(body).path("multi_product_creative_list")) {
                for(var v:c.path("video_material")){result.add(v.path("video_id").asText());result.add(v.path("video_cover_id").asText());}
                for(var image:c.path("image_material"))for(var id:image.path("image_ids"))result.add(id.asText());
                for(var card:c.path("carousel_material"))result.add(card.path("carousel_id").asText());
            }
        }catch(Exception ex){throw new IllegalStateException("Invalid durable publish payload",ex);}
        for(var s:jdbc.query("SELECT state_json FROM delivery_creation WHERE tenant_id=?",(r,n)->decode(r.getString(1)),tenant))if("CREATED".equals(s.publishStatus))for(var m:s.materials){
            if(m.carouselId!=null)result.add(m.carouselId);if(m.textAssetId!=null)result.add(m.textAssetId);
        }
        result.remove("");return result;
    }
    /** Explicit product bindings only; filenames and inferred categories are not associations. */
    public Set<String> productMedia(String tenant,String account,String product) {
        Set<String> result=new HashSet<>();
        for(var state:jdbc.query("SELECT state_json FROM delivery_creation WHERE tenant_id=?",(r,n)->decode(r.getString(1)),tenant)) {
            if(!Objects.equals(account,state.accountId))continue;
            for(var material:state.materials)if(Objects.equals(product,material.productId)) {
                if(material.videoAssetId!=null)result.add(material.videoAssetId);
                if(material.carouselId!=null)result.add(material.carouselId);
                result.addAll(material.imageAssetIds);
            }
        }
        return result;
    }
    /** Reuse selected product snapshots until the platform list provides its own product metadata. */
    public Map<String,List<CreationState.Product>> productsByPlan(String tenant,List<String> planIds) {
        Map<String,List<CreationState.Product>> result=new HashMap<>();
        summariesByPlan(tenant,planIds).forEach((id,summary)->result.put(id,summary.products()));
        return result;
    }
    public record PlanSummary(List<CreationState.Product> products,com.fasterxml.jackson.databind.JsonNode recommendations,String materialCoverAssetId) {
        public PlanSummary(List<CreationState.Product> products,com.fasterxml.jackson.databind.JsonNode recommendations){this(products,recommendations,null);}
    }
    /** Read product snapshots, budget forecasts and material covers without a query per plan. */
    public Map<String,PlanSummary> summariesByPlan(String tenant,List<String> planIds) {
        Map<String,PlanSummary> result=new HashMap<>();
        if(planIds.isEmpty())return result;
        String placeholders=String.join(",",Collections.nCopies(planIds.size(),"?"));
        var params=new ArrayList<Object>();params.add(tenant);params.addAll(planIds);
        jdbc.query("SELECT plan_id,state_json FROM delivery_creation WHERE tenant_id=? AND plan_id IN ("+placeholders+")",
                (org.springframework.jdbc.core.RowCallbackHandler)row->{
                    var state=decode(row.getString(2));
                    result.put(row.getString(1),new PlanSummary(List.copyOf(state.products),state.recommendations,
                            com.lynx.ad.modules.delivery.domain.service.PlanMaterialCover.localAsset(state)));
                },params.toArray());
        return result;
    }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
    private String encode(CreationState s) { try{return json.writeValueAsString(s);}catch(Exception e){throw new IllegalStateException(e);} }
    private CreationState decode(String s) { try{return json.readValue(s,CreationState.class);}catch(Exception e){throw new IllegalStateException(e);} }
}
