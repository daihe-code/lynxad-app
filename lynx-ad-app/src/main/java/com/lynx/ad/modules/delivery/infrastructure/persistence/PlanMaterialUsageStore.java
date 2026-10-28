package com.lynx.ad.modules.delivery.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Exact native bindings, independent of the short-lived plan list/detail display cache. */
@Repository
public class PlanMaterialUsageStore {
    public record PlanReference(String planId, String name, String status) {}
    public record Key(String accountId, String type, String id) {}
    public record Job(String planId, String tenantId, String accountId, String advertiserId, String qcPlanId, String token) {}
    public record Usage(List<PlanReference> references, boolean known) {}
    public static final class Index {
        private final Map<Key, Set<PlanReference>> references = new HashMap<>();
        private final Set<String> pending = new HashSet<>();
        private final Set<String> accounts = new HashSet<>();
        public Set<String> accounts() { return Set.copyOf(accounts); }
        public Usage lookup(Collection<Key> keys) {
            var found = new LinkedHashSet<PlanReference>();
            boolean known = true;
            for (var key : keys) {
                found.addAll(references.getOrDefault(key, Set.of()));
                if (pending.contains(key.accountId())) known = false;
            }
            return new Usage(found.stream().sorted(Comparator.comparing(PlanReference::name).thenComparing(PlanReference::planId)).toList(), known);
        }
        private void add(Key key, Collection<PlanReference> plans) {
            references.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(plans);
        }
    }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final String LIVE = " FROM ad_plan p JOIN oauth_account a ON a.id=p.oauth_account_id AND a.tenant_id=p.tenant_id "
            + "WHERE a.platform='QIANCHUAN' AND a.status='ACTIVE' AND a.grant_id IS NOT NULL "
            + "AND p.qc_plan_id IS NOT NULL AND p.qc_plan_id<>'' AND p.status NOT IN ('DRAFT','DELETED') ";
    public PlanMaterialUsageStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public void discover() {
        var rows=jdbc.query("SELECT p.id,p.tenant_id,a.id,a.advertiser_id,p.qc_plan_id"+LIVE
                + "AND NOT EXISTS (SELECT 1 FROM qc_plan_material_usage u WHERE u.plan_id=p.id)",
                (r,n)->new Job(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),null));
        for(var job:rows) try {
            jdbc.update("INSERT INTO qc_plan_material_usage(plan_id,tenant_id,account_id,advertiser_id,qc_plan_id,next_sync_at) VALUES(?,?,?,?,?,?)",
                    job.planId(),job.tenantId(),job.accountId(),job.advertiserId(),job.qcPlanId(),Timestamp.from(Instant.now()));
        } catch(DuplicateKeyException concurrentDiscovery) { /* Other application replica discovered it. */ }
    }
    public List<Job> due(int limit) {
        return jdbc.query("SELECT p.id,p.tenant_id,a.id,a.advertiser_id,p.qc_plan_id"+LIVE
                + "AND EXISTS (SELECT 1 FROM qc_plan_material_usage u WHERE u.plan_id=p.id AND u.tenant_id=p.tenant_id "
                + "AND u.account_id=a.id AND u.advertiser_id=a.advertiser_id AND u.qc_plan_id=p.qc_plan_id "
                + "AND u.next_sync_at<=? AND (u.lease_until IS NULL OR u.lease_until<?)) "
                + "ORDER BY (SELECT u.next_sync_at FROM qc_plan_material_usage u WHERE u.plan_id=p.id),p.id LIMIT ?",
                (r,n)->new Job(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),UUID.randomUUID().toString()),
                Timestamp.from(Instant.now()),Timestamp.from(Instant.now()),limit);
    }
    public boolean claim(Job job) {
        var now=Instant.now();
        return jdbc.update("UPDATE qc_plan_material_usage SET lease_until=?,lease_token=? WHERE plan_id=? AND tenant_id=? "
                + "AND account_id=? AND advertiser_id=? AND qc_plan_id=? AND next_sync_at<=? AND (lease_until IS NULL OR lease_until<?)",
                Timestamp.from(now.plusSeconds(120)),job.token(),job.planId(),job.tenantId(),job.accountId(),job.advertiserId(),job.qcPlanId(),
                Timestamp.from(now),Timestamp.from(now))==1;
    }
    public void success(Job job, JsonNode detail) {
        var bindings=bindings(job.qcPlanId(),detail);
        String data;
        try { data=json.writeValueAsString(bindings); } catch(Exception e) { throw new IllegalStateException(e); }
        var now=Instant.now();
        jdbc.update("UPDATE qc_plan_material_usage SET bindings_json=?,last_success_at=?,last_attempt_ok=TRUE,next_sync_at=?,lease_until=NULL,lease_token=NULL "
                + "WHERE plan_id=? AND tenant_id=? AND account_id=? AND advertiser_id=? AND qc_plan_id=? AND lease_token=?",
                data,Timestamp.from(now),Timestamp.from(now.plusSeconds(600)),job.planId(),job.tenantId(),job.accountId(),job.advertiserId(),job.qcPlanId(),job.token());
    }
    public void failure(Job job) {
        // Retain positive evidence from the last successful read; an error is never proof of non-use.
        jdbc.update("UPDATE qc_plan_material_usage SET last_attempt_ok=FALSE,next_sync_at=?,lease_until=NULL,lease_token=NULL WHERE plan_id=? AND lease_token=?",
                Timestamp.from(Instant.now().plusSeconds(300)),job.planId(),job.token());
    }

    /** Readback after a user opens/updates a plan supersedes an older in-flight background read. */
    public void observe(String tenant,String planId,JsonNode detail) {
        var ids=jdbc.query("SELECT qc_plan_id FROM qc_plan_material_usage WHERE tenant_id=? AND plan_id=?",(r,n)->r.getString(1),tenant,planId);
        if(ids.isEmpty())return;
        String data;
        try { data=json.writeValueAsString(bindings(ids.getFirst(),detail)); }
        catch(IllegalArgumentException incomplete) { return; }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        var now=Instant.now();
        jdbc.update("UPDATE qc_plan_material_usage SET bindings_json=?,last_success_at=?,last_attempt_ok=TRUE,next_sync_at=?,lease_until=NULL,lease_token=NULL WHERE tenant_id=? AND plan_id=?",
                data,Timestamp.from(now),Timestamp.from(now.plusSeconds(600)),tenant,planId);
    }
    public void invalidate(String tenant,String planId) {
        jdbc.update("UPDATE qc_plan_material_usage SET next_sync_at=?,last_attempt_ok=FALSE,lease_until=NULL,lease_token=NULL WHERE tenant_id=? AND plan_id=?",
                Timestamp.from(Instant.now()),tenant,planId);
    }

    public Index index(String tenant, String account) {
        var result=new Index();
        jdbc.query("SELECT p.id,p.name,p.status,a.id,u.bindings_json,u.last_attempt_ok,u.last_success_at" 
                + " FROM ad_plan p JOIN oauth_account a ON a.id=p.oauth_account_id AND a.tenant_id=p.tenant_id "
                + "LEFT JOIN qc_plan_material_usage u ON u.plan_id=p.id AND u.tenant_id=p.tenant_id AND u.account_id=a.id "
                + "AND u.advertiser_id=a.advertiser_id AND u.qc_plan_id=p.qc_plan_id "
                + "WHERE p.tenant_id=? AND a.platform='QIANCHUAN' AND a.status='ACTIVE' AND a.grant_id IS NOT NULL "
                + "AND p.qc_plan_id IS NOT NULL AND p.qc_plan_id<>'' AND p.status NOT IN ('DRAFT','DELETED') "
                + (account==null||account.isBlank()?"":"AND a.id=?"), r->{
                    String accountId=r.getString(4);
                    result.accounts.add(accountId);
                    if(!r.getBoolean(6)||r.getTimestamp(7)==null||r.getTimestamp(7).toInstant().isBefore(Instant.now().minusSeconds(900))) result.pending.add(accountId);
                    String data=r.getString(5); if(data==null)return;
                    var plan=new PlanReference(r.getString(1),r.getString(2),r.getString(3));
                    try { for(var key:json.readTree(data)) result.add(new Key(accountId,key.path("type").asText(),key.path("id").asText()),List.of(plan)); }
                    catch(Exception e) { result.pending.add(accountId); }
                }, account==null||account.isBlank()?new Object[]{tenant}:new Object[]{tenant,account});
        addCarouselAliases(tenant,result);
        return result;
    }

    private void addCarouselAliases(String tenant, Index index) {
        // Only native receipts under the same currently authorized advertiser can connect a group to its parts.
        jdbc.query("SELECT viewer.id,cp.tenant_id,cp.carousel_id,cp.qc_carousel_id,part.role,part.asset_id,s.object_id,s.material_id "
                + "FROM oauth_account viewer JOIN oauth_account publisher ON publisher.advertiser_id=viewer.advertiser_id "
                + "AND publisher.platform='QIANCHUAN' AND publisher.status='ACTIVE' AND publisher.grant_id IS NOT NULL "
                + "JOIN creative_carousel_platform cp ON cp.account_id=publisher.id AND cp.tenant_id=publisher.tenant_id AND cp.status='SUCCEEDED' "
                + "LEFT JOIN creative_carousel_part part ON part.tenant_id=cp.tenant_id AND part.carousel_id=cp.carousel_id "
                + "LEFT JOIN creative_asset_platform s ON s.tenant_id=part.tenant_id AND s.asset_id=part.asset_id AND s.account_id=publisher.id "
                + "AND s.advertiser_id=publisher.advertiser_id AND s.status='UPLOADED' "
                + "WHERE viewer.tenant_id=? AND viewer.platform='QIANCHUAN' AND viewer.status='ACTIVE' AND viewer.grant_id IS NOT NULL",r->{
                    String account=r.getString(1);
                    var refs=index.references.get(new Key(account,"CAROUSEL",r.getString(4)));
                    if(refs==null||refs.isEmpty())return;
                    if(tenant.equals(r.getString(2))) {
                        index.add(new Key(account,"CAROUSEL",r.getString(3)),refs);
                        if(r.getString(5)!=null)index.add(new Key(account,r.getString(5),r.getString(6)),refs);
                    }
                    if(r.getString(5)!=null)for(int field:new int[]{7,8})if(r.getString(field)!=null)
                        index.add(new Key(account,r.getString(5),r.getString(field)),refs);
                },tenant);
    }

    public record Binding(String type,String id) {}
    public static Set<Binding> bindings(String expectedId,JsonNode detail) {
        if(detail==null||!expectedId.equals(detail.path("ad_id").asText())||!detail.path("multi_product_creative_list").isArray())
            throw new IllegalArgumentException("Incomplete or mismatched native plan detail");
        var keys=new LinkedHashSet<Binding>();
        var groups=new ArrayList<JsonNode>();detail.path("multi_product_creative_list").forEach(groups::add);
        if(detail.path("programmatic_creative_media_list").isObject())groups.add(detail.path("programmatic_creative_media_list"));
        for(var group:groups) {
            boolean hasMaterials=false;
            for(String field:List.of("video_material","image_material","carousel_material","title_material")) {
                // Native ad/detail explicitly uses null for an empty material category.
                if(group.has(field) && (group.path(field).isArray() || group.path(field).isNull()))hasMaterials=true;
                else if(group.hasNonNull(field))throw new IllegalArgumentException("Malformed native creative group");
            }
            if(!hasMaterials)throw new IllegalArgumentException("Incomplete native creative group");
            for(var video:group.path("video_material")) {
                for(String field:List.of("video_id","aweme_item_id","material_id"))add(keys,"VIDEO",video.path(field));
                add(keys,"IMAGE",video.path("video_cover_id"));
            }
            for(var image:group.path("image_material")) {
                for(var id:image.path("image_ids"))add(keys,"IMAGE",id);
                add(keys,"IMAGE",image.path("material_id"));
            }
            for(var carousel:group.path("carousel_material")) {
                for(String field:List.of("carousel_id","aweme_carousel_id","material_id"))add(keys,"CAROUSEL",carousel.path(field));
            }
        }
        return keys;
    }
    private static void add(Set<Binding> keys,String type,JsonNode value) {
        if(value.isValueNode()&&!value.isNull()&&!value.asText().isBlank()&&!"0".equals(value.asText()))keys.add(new Binding(type,value.asText()));
    }
}
