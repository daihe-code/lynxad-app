package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.modules.delivery.api.dto.*;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanRemoteStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.audit.application.*;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.common.security.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PlanManagementService {
    public record Material(String id,String name,String coverUrl,String auditStatus,String materialStatus,boolean eligible,String reason,Long impressions) {}
    public record ControlResult(String taskId,String status) {}
    private final AdPlanRepository plans;
    private final AdManagementPort port;
    private final QianchuanCredentialFacade credentials;
    private final PlanRemoteStore store;
    private final PlanQueryService query;
    private final BillingTrialService billing;
    private final AuditRecorder audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PlanManagementService(AdPlanRepository plans,AdManagementPort port,QianchuanCredentialFacade credentials,
                                 PlanRemoteStore store,PlanQueryService query,BillingTrialService billing,AuditRecorder audit,
                                 JdbcTemplate jdbc,ObjectMapper json) {
        this.plans=plans;this.port=port;this.credentials=credentials;this.store=store;this.query=query;this.billing=billing;this.audit=audit;this.jdbc=jdbc;this.json=json;
    }
    private AdPlan require(TenantId tenant,PlanId id) { return plans.findById(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在")); }
    private void editable(AdPlan plan) {
        if(plan.qcPlanId().isEmpty() || (plan.status()!=PlanStatus.DELIVERING && plan.status()!=PlanStatus.PAUSED))
            throw new BizException(1403,"仅支持修改投放中或已暂停的千川计划");
    }
    public PlanVo detail(TenantId tenant,PlanId id) {
        synchronized(store) { return detailLocked(tenant,id); }
    }
    private PlanVo detailLocked(TenantId tenant,PlanId id) {
        AdPlan plan=require(tenant,id);
        if(plan.qcPlanId().isPresent()) {
            var c=credentials.getUsableCredential(tenant,plan.accountId());
            JsonNode detail=port.detail(c,plan.qcPlanId().get().value());
            // Mock responses do not claim to contain platform settings.
            if(detail.has("delivery_setting")) {
                var setting=detail.path("delivery_setting");
                if(detail.hasNonNull("name")) plan.rename(PlanName.fromPlatform(detail.path("name").asText()));
                if(detail.hasNonNull("status")) { plan.syncPlatformStatus(detail.path("status").asText());plan.apply(QianchuanPlanMapping.status(plan.qcStatus())); }
                BigDecimal budget=QianchuanPlanMapping.number(setting,"budget");
                var mode=setting.hasNonNull("smart_bid_type")?SmartBidType.fromOcean(setting.path("smart_bid_type").asText()):plan.setting().bidType();
                var schedule=new Schedule(plan.setting().schedule().type(),
                        setting.has("start_time")?QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting,"start_time")):plan.setting().schedule().startDate(),
                        setting.has("end_time")?QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting,"end_time")):plan.setting().schedule().endDate());
                plan.updateSetting(new DeliverySetting(mode,budget==null?plan.setting().dailyBudget():Money.ofYuan(budget),
                        Optional.ofNullable(QianchuanPlanMapping.number(setting,"roi2_goal")).map(RoiGoal::new),schedule,plan.setting().audience()));
                plans.save(plan);
                if(store.findId(tenant,plan.accountId(),plan.qcPlanId().get().value()).isEmpty())
                    store.save(tenant,plan.accountId(),plan.qcPlanId().get().value(),id.value(),detail);
                store.detail(tenant,id.value(),detail);
            }
        }
        return query.get(tenant,id);
    }
    /** Serialized per process; platform acknowledgement always precedes local mutation. */
    public PlanVo settings(TenantId tenant,PlanId id,PlanSettingsRequest request) {
        synchronized(store) { return settingsLocked(tenant,id,request); }
    }
    private PlanVo settingsLocked(TenantId tenant,PlanId id,PlanSettingsRequest request) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        if(request.name()==null && request.budget()==null && request.roi2Goal()==null) throw new BizException(1402,"请填写要修改的字段");
        String name=request.name()==null?null:new PlanName(request.name()).text();
        if(request.budget()!=null) number(request.budget(),new BigDecimal("300"),new BigDecimal("999999999.99"),"日预算");
        if(request.roi2Goal()!=null) number(request.roi2Goal(),new BigDecimal("0.01"),new BigDecimal("100"),"净成交ROI目标");
        AdPlan plan=require(tenant,id); editable(plan);
        if(request.roi2Goal()!=null && plan.setting().bidType()!=SmartBidType.CUSTOM) throw new BizException(1402,"放量投放不设置净成交ROI目标");
        var c=credentials.getUsableCredential(tenant,plan.accountId());
        var detail=port.detail(c,plan.qcPlanId().get().value());
        if(request.roi2Goal()!=null && detail.path("delivery_setting").hasNonNull("smart_bid_type") &&
                SmartBidType.fromOcean(detail.path("delivery_setting").path("smart_bid_type").asText())!=SmartBidType.CUSTOM)
            throw new BizException(1403,"平台投放方式已变化，请先同步计划");
        if(detail.hasNonNull("status") && QianchuanPlanMapping.status(detail.path("status").asText())!=PlanStatus.DELIVERING &&
                QianchuanPlanMapping.status(detail.path("status").asText())!=PlanStatus.PAUSED) throw new BizException(1403,"平台计划状态已变化，请先同步");
        if(name!=null && !name.equals(plan.name().text()) && plans.existsUnfinishedName(tenant,plan.awemeId(),new PlanName(name),Optional.of(id)))
            throw new BizException(1401,"同一抖音号存在同名计划");
        String before=plan.name().text()+" / "+plan.setting().dailyBudget().amount()+" / "+plan.setting().roi2Goal().map(RoiGoal::value).orElse(null);
        port.settings(c,plan.qcPlanId().get().value(),name,request.budget(),request.roi2Goal());
        if(name!=null) plan.rename(new PlanName(name));
        BigDecimal currentBudget=QianchuanPlanMapping.number(detail.path("delivery_setting"),"budget");
        BigDecimal currentRoi=QianchuanPlanMapping.number(detail.path("delivery_setting"),"roi2_goal");
        plan.updateSetting(new DeliverySetting(plan.setting().bidType(),request.budget()!=null?Money.ofYuan(request.budget()):currentBudget!=null?Money.ofYuan(currentBudget):plan.setting().dailyBudget(),
                request.roi2Goal()!=null?Optional.of(new RoiGoal(request.roi2Goal())):detail.has("delivery_setting")?Optional.ofNullable(currentRoi).map(RoiGoal::new):plan.setting().roi2Goal(),plan.setting().schedule(),plan.setting().audience()));
        try {
            plans.save(plan);
            var cached=store.list(tenant).get(id.value());
            if(cached!=null) {
                ObjectNode current=(detail.isObject()?detail:json.createObjectNode()).deepCopy();
                current.put("name",plan.name().text());
                ObjectNode setting=current.withObject("/delivery_setting");
                setting.put("budget",plan.setting().dailyBudget().amount());
                plan.setting().roi2Goal().ifPresent(goal->setting.put("roi2_goal",goal.value()));
                store.detail(tenant,id.value(),current);
            }
            record(tenant,id,"修改计划："+plan.name().text(),"修改前："+before);
        } catch(RuntimeException ex) { throw new BizException(1406,"千川已接受修改，本地保存失败，请同步核查，勿重复提交"); }
        return query.get(tenant,id);
    }
    private static void number(BigDecimal value,BigDecimal min,BigDecimal max,String label) {
        if(value.scale()>2 || value.compareTo(min)<0 || value.compareTo(max)>0) throw new BizException(1402,label+"范围为 "+min+"–"+max+"，最多两位小数");
    }
    public List<Material> materials(TenantId tenant,PlanId id) {
        AdPlan plan=require(tenant,id);
        if(plan.qcPlanId().isEmpty()) return List.of();
        return materialRows(port.materials(credentials.getUsableCredential(tenant,plan.accountId()),plan.qcPlanId().get().value()));
    }
    public static List<Material> materialRows(List<JsonNode> rows) {
        List<Material> result=new ArrayList<>();
        for(JsonNode row:rows) {
            JsonNode video=row.path("material_info").path("video_material");
            if(!video.hasNonNull("material_id")) continue;
            String audit=row.path("audit_status").asText("UNKNOWN"),status=row.path("material_status").asText("UNKNOWN");
            boolean eligible="PASS".equals(audit) && "DELIVERY_OK".equals(status) && "AD".equals(video.path("source").asText()) && !row.path("is_delete").asBoolean();
            BigDecimal impressions=QianchuanPlanMapping.number(row.path("stats_info"),"show_cnt");
            result.add(new Material(video.path("material_id").asText(),video.path("title").asText("未命名视频"),
                    video.path("cover_image").path("url").asText(null),audit,status,eligible,eligible?null:"需为自建、审核通过且正在投放的视频",
                    impressions==null?null:impressions.longValue()));
        }
        return List.copyOf(result);
    }
    public ControlResult control(TenantId tenant,PlanId id,ControlTaskRequest request) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        if("搜索抢首屏".equals(request.kind())) throw new BizException(1403,"搜索抢首屏专用接口尚未核验，暂不可提交");
        String scene=switch(request.kind()==null?"":request.kind()) { case "一键起量"->"SMART_BOOST";case "素材追投"->"MATERIAL_ADD_BUDGET";default->throw new BizException(1402,"调控类型不正确"); };
        AdPlan plan=require(tenant,id); editable(plan);
        if(plan.status()!=PlanStatus.DELIVERING) throw new BizException(1403,"调控工具仅支持投放中的计划");
        number(request.budget()==null?BigDecimal.ZERO:request.budget(),new BigDecimal("100.01"),plan.setting().dailyBudget().amount(),"调控预算");
        if(request.hours()==null || request.hours().compareTo(new BigDecimal("0.5"))<0 || request.hours().compareTo(new BigDecimal("24"))>0 || request.hours().remainder(new BigDecimal("0.5")).signum()!=0)
            throw new BizException(1402,"调控时长为 0.5–24 小时，步进 0.5 小时");
        if(request.name()==null || request.name().isBlank() || request.name().length()>50) throw new BizException(1402,"任务名称须 1–50 字");
        if(request.requestKey()==null || !request.requestKey().matches("[a-zA-Z0-9_-]{8,64}")) throw new BizException(1402,"缺少有效提交标识");
        List<String> ids=request.materialIds()==null?List.of():request.materialIds().stream().distinct().toList();
        if("MATERIAL_ADD_BUDGET".equals(scene)) {
            if(ids.isEmpty() || ids.size()>20) throw new BizException(1402,"请选择 1–20 个视频素材");
            Set<String> eligible=new HashSet<>(); materials(tenant,id).stream().filter(Material::eligible).forEach(m->eligible.add(m.id()));
            if(!eligible.containsAll(ids)) throw new BizException(1402,"所选素材追投资格已变化，请重新加载素材");
        }
        var c=credentials.getUsableCredential(tenant,plan.accountId());
        var current=port.detail(c,plan.qcPlanId().get().value());
        if(current.hasNonNull("status") && QianchuanPlanMapping.status(current.path("status").asText())!=PlanStatus.DELIVERING)
            throw new BizException(1403,"平台计划当前未投放，请先同步状态");
        BigDecimal liveBudget=QianchuanPlanMapping.number(current.path("delivery_setting"),"budget");
        if(liveBudget!=null && request.budget().compareTo(liveBudget)>0) throw new BizException(1402,"调控预算不得超过平台当前日预算，请先同步");
        String hash;
        try {
            Map<String,Object> canonical=new TreeMap<>(); canonical.put("planId",id.value());canonical.put("kind",request.kind());
            canonical.put("name",request.name().trim());canonical.put("budget",request.budget().stripTrailingZeros().toPlainString());
            canonical.put("hours",request.hours().stripTrailingZeros().toPlainString());canonical.put("materials",ids.stream().sorted().toList());
            hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical)));
        }
        catch(Exception ex) { throw new BizException(1402,"提交参数不正确"); }
        try { jdbc.update("INSERT INTO qc_control_submission(tenant_id,request_key,plan_id,request_hash,status,created_at) VALUES(?,?,?,?,?,?)",
                tenant.value(),request.requestKey(),id.value(),hash,"PENDING",java.sql.Timestamp.from(Instant.now())); }
        catch(DuplicateKeyException ex) {
            var matched=jdbc.queryForList("SELECT request_hash,status,task_id FROM qc_control_submission WHERE tenant_id=? AND request_key=?",tenant.value(),request.requestKey());
            var previous=matched.isEmpty()?jdbc.queryForMap("SELECT request_hash,status,task_id FROM qc_control_submission WHERE tenant_id=? AND request_hash=?",tenant.value(),hash):matched.getFirst();
            if(!hash.equals(previous.get("request_hash"))) throw new BizException(1402,"同一提交标识不能用于不同参数");
            if("SUCCEEDED".equals(previous.get("status"))) return new ControlResult(String.valueOf(previous.get("task_id")),"SUCCEEDED");
            throw new BizException(1406,"该提交结果尚未确认，请核查任务列表，勿重复提交");
        }
        try {
            String task=port.control(c,plan.qcPlanId().get().value(),scene,request.name().trim(),request.budget(),request.hours(),ids);
            jdbc.update("UPDATE qc_control_submission SET status='SUCCEEDED',task_id=? WHERE tenant_id=? AND request_key=?",task,tenant.value(),request.requestKey());
            record(tenant,id,"创建"+request.kind()+"任务："+request.name(),"任务ID："+task);
            return new ControlResult(task,"SUCCEEDED");
        } catch(RuntimeException ex) {
            jdbc.update("UPDATE qc_control_submission SET status='UNKNOWN' WHERE tenant_id=? AND request_key=? AND status='PENDING'",tenant.value(),request.requestKey());
            throw new BizException(1406,"调控提交结果未确认："+ex.getMessage()+"。请核查任务列表后再操作");
        }
    }
    public List<JsonNode> controls(TenantId tenant,PlanId id) {
        AdPlan plan=require(tenant,id);
        return plan.qcPlanId().isEmpty()?List.of():port.controls(credentials.getUsableCredential(tenant,plan.accountId()),plan.qcPlanId().get().value());
    }
    private void record(TenantId tenant,PlanId id,String label,String extra) {
        var principal=TenantContext.require();
        audit.record(new SuccessAuditCommand(tenant,principal.userId(),"商家",AuditActionType.PLAN,"PLAN",id.value(),label,
                RequestMeta.ip().orElse(null),principal.sessionId().orElse(null),RequestMeta.userAgent().orElse(null),extra));
    }
}
