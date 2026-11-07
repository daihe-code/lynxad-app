package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;
import com.lynx.ad.modules.delivery.domain.service.PlanEditVersion;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanSettingsEditStore;

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
    public record ControlTaskPoint(String time,Map<String,BigDecimal> metrics) {}
    public record ControlTaskReport(JsonNode task,Map<String,BigDecimal> summary,List<ControlTaskPoint> trend,String granularity,Instant fetchedAt,String warning) {}
    private final AdPlanRepository plans;
    private final AdManagementPort port;
    private final QianchuanCredentialFacade credentials;
    private final PlanRemoteStore store;
    private final PlanQueryService query;
    private final BillingTrialService billing;
    private final AuditRecorder audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore transfers;
    @org.springframework.beans.factory.annotation.Autowired public void transferStore(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore value) { transfers=value; }
    private com.lynx.ad.modules.creative.application.AssetUploadService assets;
    @org.springframework.beans.factory.annotation.Autowired public void assetUploadService(com.lynx.ad.modules.creative.application.AssetUploadService value) { assets=value; }
    private PlanSettingsEditStore settingsEdits;
    @org.springframework.beans.factory.annotation.Autowired public void settingsEdits(PlanSettingsEditStore value){settingsEdits=value;}
    public PlanManagementService(AdPlanRepository plans,AdManagementPort port,QianchuanCredentialFacade credentials,
                                 PlanRemoteStore store,PlanQueryService query,BillingTrialService billing,AuditRecorder audit,
                                 JdbcTemplate jdbc,ObjectMapper json) {
        this.plans=plans;this.port=port;this.credentials=credentials;this.store=store;this.query=query;this.billing=billing;this.audit=audit;this.jdbc=jdbc;this.json=json;
    }
    private AdPlan require(TenantId tenant,PlanId id) { return plans.findById(tenant,id).orElseThrow(()->new BizException(1405,"计划不存在")); }
    public record MonitoringFacts(String qcId,String status,String bidType,BigDecimal budget,BigDecimal roi,List<String> productIds,String deepExternalAction,String qcStatus) {
        public MonitoringFacts(String qcId,String status,String bidType,BigDecimal budget,BigDecimal roi,List<String> products){this(qcId,status,bidType,budget,roi,products,null,null);}
    }
    /** Read the physical plan without submitting an operation or deriving membership from a draft. */
    public MonitoringFacts monitoringFacts(TenantId tenant,PlanId id) {
        var p=require(tenant,id);String qc=p.qcPlanId().orElseThrow(()->new BizException(1403,"草稿尚未发布")).value();
        var row=port.detail(credentials.getUsableCredential(tenant,p.accountId()),qc);
        if(!qc.equals(row.path("ad_id").asText()) || !row.hasNonNull("status") || !row.has("delivery_setting"))throw new BizException(1406,"平台计划信息不完整，暂无法盯盘");
        var setting=row.path("delivery_setting");var budget=QianchuanPlanMapping.number(setting,"budget");
        if(budget==null || budget.signum()<=0 || !setting.hasNonNull("smart_bid_type"))throw new BizException(1406,"平台计划预算或模式缺失");
        String qcStatus=row.path("status").asText();
        var status=PlanEditVersion.platformStatus(row);
        if(status==PlanStatus.PAUSED && "DISABLE".equals(row.path("opt_status").asText()))qcStatus="DISABLE";
        return new MonitoringFacts(qc,status.name(),SmartBidType.fromOcean(setting.path("smart_bid_type").asText()).name(),
            budget,QianchuanPlanMapping.number(setting,"roi2_goal"),List.copyOf(PlanProductsService.boundProducts(row,qc).keySet()),
            QianchuanPlanMapping.text(setting,"deep_external_action"),qcStatus);
    }
    private void editable(AdPlan plan) {
        if(plan.qcPlanId().isEmpty() || (plan.status()!=PlanStatus.DELIVERING && plan.status()!=PlanStatus.PAUSED))
            throw new BizException(1403,"仅支持修改投放中或已暂停的千川计划");
    }
    public PlanVo detail(TenantId tenant,PlanId id) {
        var plan=require(tenant,id);
        if(plan.qcPlanId().isPresent()) {
            if(settingsEdits.pending(tenant,id).isPresent())throw new BizException(1523,"上次计划修改尚未确认，请使用核查修改结果");
            String version=settingsEdits.state(plan).version();
            var detail=port.detail(credentials.getUsableCredential(tenant,plan.accountId()),plan.qcPlanId().orElseThrow().value());
            settingsEdits.applyRead(plan,version,()->cacheDetail(tenant,plan,detail));
        }
        return query.get(tenant,id);
    }
    /** A durable claim precedes dispatch. No database transaction is held while calling Qianchuan. */
    public PlanVo settings(TenantId tenant,PlanId id,PlanSettingsRequest request) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        if(request.name()==null && request.budget()==null && request.roi2Goal()==null && request.scheduleType()==null)throw new BizException(1402,"请填写要修改的字段");
        String name=request.name()==null?null:new PlanName(request.name()).text();
        var current=require(tenant,id);editable(current);
        validateSettings(current,request);
        if(transfers!=null)transfers.assertIdle(tenant,id);
        var claim=settingsEdits.claim(tenant,id,new PlanSettingsRequest(name,request.budget(),request.roi2Goal(),request.expectedVersion(),request.scheduleType(),request.endDate()));
        var plan=claim.plan();var pending=claim.pending();boolean sent=false;
        try {
            editable(plan);validateSettings(plan,request);
            var c=credentials.getUsableCredential(tenant,plan.accountId());
            var detail=port.detail(c,plan.qcPlanId().orElseThrow().value());
            PlanEditVersion.platformUnchanged(plan,detail,request.scheduleType()!=null);
            if(request.scheduleType()!=null && !com.lynx.ad.modules.delivery.domain.service.PlanSchedule.complete(detail.path("delivery_setting")))
                throw new BizException(1406,"千川未返回完整投放日期，暂不提交修改");
            if(name!=null && !name.equals(plan.name().text()) && plans.existsUnfinishedName(tenant,plan.awemeId(),new PlanName(name),Optional.of(id)))
                throw new BizException(1401,"同一抖音号存在同名计划");
            String before=plan.name().text()+" / "+plan.setting().dailyBudget().amount()+" / "+plan.setting().roi2Goal().map(RoiGoal::value).orElse(null);
            settingsEdits.dispatched(pending);sent=true;
            if(request.roi2Goal()!=null)port.settings(c,plan.qcPlanId().orElseThrow().value(),name,request.budget(),request.roi2Goal(),QianchuanPlanMapping.text(detail.path("delivery_setting"),"deep_external_action"));
            else if(name!=null || request.budget()!=null) port.settings(c,plan.qcPlanId().orElseThrow().value(),name,request.budget(),null);
            if(request.scheduleType()!=null)port.schedule(c,plan.qcPlanId().orElseThrow().value(),request.scheduleType(),request.endDate());
            var actual=port.detail(c,plan.qcPlanId().orElseThrow().value());
            completeFacts(plan,actual);
            if(!matches(request,actual))throw new BizException(1406,"千川已接收修改，但尚未确认全部修改值，请核查结果，勿重复提交");
            settingsEdits.complete(pending,()->{cacheDetail(tenant,plan,actual);record(tenant,id,"修改计划："+plan.name().text(),"修改前："+before);});
        }catch(RuntimeException failure) {
            if(sent){settingsEdits.uncertain(pending);throw new BizException(1406,"计划修改结果待确认，请先核查千川当前设置，勿重复提交");}
            settingsEdits.complete(pending,()->{});
            if(failure instanceof BizException business && business.code()==1406)throw new BizException(1404,"尚未发送修改，千川当前设置暂无法核验，请读取最新值并核对；当前输入已保留");
            throw failure;
        }
        return query.get(tenant,id);
    }
    private void validateSettings(AdPlan p,PlanSettingsRequest request) {
        if(request.scheduleType()!=null) {
            if(!java.util.Set.of("FROM_NOW","DATE_RANGE").contains(request.scheduleType()))throw new BizException(1402,"投放日期类型不正确");
            var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
            if("DATE_RANGE".equals(request.scheduleType()) && (request.endDate()==null || request.endDate().isBefore(today) || p.setting().schedule().startDate()!=null && request.endDate().isBefore(p.setting().schedule().startDate())))
                throw new BizException(1402,"结束日期不得早于今天或原计划开始日期");
            if("FROM_NOW".equals(request.scheduleType()) && request.endDate()!=null)throw new BizException(1402,"长期投放不设置结束日期");
        } else if(request.endDate()!=null)throw new BizException(1402,"请选择投放日期类型");
        if(request.budget()!=null)DeliveryInputPolicy.budget(request.budget(),p.setting().bidType(),DeliveryInputPolicy.RULES.update());
        if(request.roi2Goal()!=null) {
            if(p.setting().bidType()!=SmartBidType.CUSTOM)throw new BizException(1402,"放量投放不设置净成交ROI目标");
            DeliveryInputPolicy.roi(request.roi2Goal(),DeliveryInputPolicy.RULES.update());
        }
    }
    public record SettingsVerification(String expectedVersion,boolean acceptCurrent) {}
    public record SettingsResult(PlanVo plan,String status,String message,boolean canAcceptCurrent) {}
    /** Reads platform state and clears the claim only after proof, or an explicit acceptance of current facts. */
    public SettingsResult verifySettings(TenantId tenant,PlanId id,SettingsVerification request) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        var pending=settingsEdits.verification(tenant,id,request.expectedVersion());
        try {
            var plan=require(tenant,id);var target=settingsEdits.request(pending);
            var actual=port.detail(credentials.getUsableCredential(tenant,plan.accountId()),plan.qcPlanId().orElseThrow().value());
            completeFacts(plan,actual);
            boolean applied=matches(target,actual),canAccept=pending.startedAt()!=null&&!pending.startedAt().plusSeconds(180).isAfter(Instant.now());
            if(!applied && request.acceptCurrent() && !canAccept)throw new BizException(1523,"请等待原修改请求完成后再采用平台当前设置");
            // Accept only the values the user reviewed; a newer console edit needs another comparison.
            if(!applied && request.acceptCurrent())PlanEditVersion.platformUnchanged(plan,actual);
            if(applied || request.acceptCurrent()) {
                settingsEdits.complete(pending,()->{cacheDetail(tenant,plan,actual);record(tenant,id,applied?"核查计划修改成功":"核查并采用千川当前设置",applied?"已确认全部请求值":"用户接受平台当前设置，不代表原修改全部成功");});
                return new SettingsResult(query.get(tenant,id),applied?"CONFIRMED":"ACCEPTED_CURRENT",applied?"已确认全部修改，计划设置已同步":"已采用千川当前设置，可核对后重新编辑；本次未重新发送修改请求",false);
            }
            settingsEdits.observed(pending,()->cacheDetail(tenant,plan,actual));
            return new SettingsResult(query.get(tenant,id),"UNCERTAIN","尚未确认全部修改，已读取千川当前值；请核对后再决定是否采用当前设置",canAccept);
        }catch(RuntimeException failure){settingsEdits.uncertain(pending);throw failure;}
    }
    private static boolean matches(PlanSettingsRequest request,JsonNode detail) {
        var ds=detail.path("delivery_setting");
        return (request.name()==null||request.name().equals(DeliveryInputPolicy.text(detail.path("name").asText()))) &&
            (request.budget()==null||equal(request.budget(),QianchuanPlanMapping.number(ds,"budget"))) &&
            (request.roi2Goal()==null||equal(request.roi2Goal(),QianchuanPlanMapping.number(ds,"roi2_goal"))) &&
            (request.scheduleType()==null || com.lynx.ad.modules.delivery.domain.service.PlanSchedule.complete(ds) &&
                request.scheduleType().equals(com.lynx.ad.modules.delivery.domain.service.PlanSchedule.type(ds).name()) &&
                ("FROM_NOW".equals(request.scheduleType()) || request.endDate().equals(QianchuanPlanMapping.date(QianchuanPlanMapping.text(ds,"end_time")))));
    }
    private static boolean equal(BigDecimal a,BigDecimal b){return a!=null&&b!=null&&a.compareTo(b)==0;}
    private static void completeFacts(AdPlan plan,JsonNode detail) {
        var ds=detail.path("delivery_setting");
        if(!plan.qcPlanId().orElseThrow().value().equals(detail.path("ad_id").asText())||!detail.hasNonNull("name")||!detail.hasNonNull("status")||!PlanEditVersion.validMode(ds)||QianchuanPlanMapping.number(ds,"budget")==null||SmartBidType.fromOcean(ds.path("smart_bid_type").asText())==SmartBidType.CUSTOM&&!ds.has("roi2_goal"))
            throw new BizException(1406,"千川未返回完整计划设置，请稍后核查");
    }
    private void cacheDetail(TenantId tenant,AdPlan plan,JsonNode detail) {
        // Read-only sync may also see old mock responses without business settings.
        if(!detail.has("delivery_setting"))return;
        plan.restorePlatformAweme(QianchuanPlanMapping.text(detail,"aweme_id"));
        var setting=detail.path("delivery_setting");
        if(detail.hasNonNull("name"))plan.rename(PlanName.fromPlatform(detail.path("name").asText()));
        if(detail.hasNonNull("status")){plan.syncPlatformStatus(detail.path("status").asText());plan.apply(PlanEditVersion.platformStatus(detail));}
        var mode=setting.hasNonNull("smart_bid_type")?SmartBidType.fromOcean(setting.path("smart_bid_type").asText()):plan.setting().bidType();
        var budget=QianchuanPlanMapping.number(setting,"budget");
        var schedule=com.lynx.ad.modules.delivery.domain.service.PlanSchedule.read(setting,plan.setting().schedule());
        plan.updateSetting(new DeliverySetting(mode,budget==null?plan.setting().dailyBudget():Money.ofYuan(budget),Optional.ofNullable(QianchuanPlanMapping.number(setting,"roi2_goal")).map(RoiGoal::new),schedule,plan.setting().audience()));
        plans.save(plan);
        if(store.findId(tenant,plan.accountId(),plan.qcPlanId().orElseThrow().value()).isEmpty())store.save(tenant,plan.accountId(),plan.qcPlanId().orElseThrow().value(),plan.id().value(),detail);
        store.detail(tenant,plan.id().value(),detail);
    }
    private static void number(BigDecimal value,BigDecimal min,BigDecimal max,String label) {
        if(value.scale()>2 || value.compareTo(min)<0 || value.compareTo(max)>0) throw new BizException(1402,label+"范围为 "+min+"–"+max+"，最多两位小数");
    }
    public List<Material> materials(TenantId tenant,PlanId id) {
        AdPlan plan=require(tenant,id);
        if(plan.qcPlanId().isEmpty()) return List.of();
        var rows=port.materials(credentials.getUsableCredential(tenant,plan.accountId()),plan.qcPlanId().get().value());
        Set<String> uploadedVideoIds=new HashSet<>();
        if(assets!=null)for(JsonNode row:rows) {
            JsonNode video=row.path("material_info").path("video_material");
            String videoId=video.path("video_id").asText(null);
            if(!video.hasNonNull("source") && "CUSTOM".equals(row.path("material_select_type").asText()) && assets.ownsUploadedVideo(tenant,plan.accountId().value(),videoId))uploadedVideoIds.add(videoId);
        }
        return materialRows(rows,uploadedVideoIds);
    }
    public static List<Material> materialRows(List<JsonNode> rows) {
        return materialRows(rows,Set.of());
    }
    public static List<Material> materialRows(List<JsonNode> rows,Set<String> uploadedVideoIds) {
        List<Material> result=new ArrayList<>();
        for(JsonNode row:rows) {
            JsonNode video=row.path("material_info").path("video_material");
            if(!video.hasNonNull("material_id")) continue;
            String audit=row.path("audit_status").asText("UNKNOWN"),status=row.path("material_status").asText("UNKNOWN");
            boolean ownVideo="AD".equals(video.path("source").asText()) || !video.hasNonNull("source") && "CUSTOM".equals(row.path("material_select_type").asText()) && uploadedVideoIds.contains(video.path("video_id").asText());
            boolean eligible="PASS".equals(audit) && "DELIVERY_OK".equals(status) && ownVideo && !row.path("is_delete").asBoolean();
            BigDecimal impressions=QianchuanPlanMapping.number(row.path("stats_info"),"product_show_count_for_roi2");
            if(impressions==null)impressions=QianchuanPlanMapping.number(row.path("stats_info"),"show_cnt");
            result.add(new Material(video.path("material_id").asText(),video.path("title").asText("未命名视频"),
                    video.path("cover_image").path("image_url").asText(video.path("cover_image").path("url").asText(null)),audit,status,eligible,eligible?null:"需为自建、审核通过且正在投放的视频",
                    impressions==null?null:impressions.longValue()));
        }
        return List.copyOf(result);
    }
    public ControlResult control(TenantId tenant,PlanId id,ControlTaskRequest request) {
        if(transfers!=null)transfers.assertIdle(tenant,id);
        billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
        if("搜索抢首屏".equals(request.kind())) throw new BizException(1403,"搜索抢首屏暂不支持在灵投提交，请在千川后台操作");
        String scene=switch(request.kind()==null?"":request.kind()) { case "一键起量"->"SMART_BOOST";case "素材追投"->"MATERIAL_ADD_BUDGET";default->throw new BizException(1402,"调控类型不正确"); };
        AdPlan plan=require(tenant,id); editable(plan);
        if(plan.status()!=PlanStatus.DELIVERING) throw new BizException(1403,"调控工具仅支持投放中的计划");
        number(request.budget()==null?BigDecimal.ZERO:request.budget(),new BigDecimal("100"),plan.setting().dailyBudget().amount(),"调控预算");
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
        if(plan.status()==PlanStatus.DELETED)throw new BizException(1405,"计划不存在");
        return plan.qcPlanId().isEmpty()?List.of():port.controls(credentials.getUsableCredential(tenant,plan.accountId()),plan.qcPlanId().get().value());
    }
    public List<JsonNode> controls(TenantId tenant,PlanId id,String kind) {
        return controls(tenant,id,kind,null);
    }
    public List<JsonNode> controls(TenantId tenant,PlanId id,String kind,PlanStatisticsRange range) {
        if(kind==null || kind.isBlank()) {
            if(range!=null)throw new BizException(1402,"请选择调控类型");
            return controls(tenant,id);
        }
        String scene=switch(kind) { case "一键起量"->"SMART_BOOST";case "素材追投"->"MATERIAL_ADD_BUDGET";default->throw new BizException(1402,"调控类型不正确"); };
        AdPlan plan=require(tenant,id);
        if(plan.status()==PlanStatus.DELETED)throw new BizException(1405,"计划不存在");
        if(plan.qcPlanId().isEmpty())return List.of();
        var credential=credentials.getUsableCredential(tenant,plan.accountId());String qcId=plan.qcPlanId().get().value();
        return range==null?port.controls(credential,qcId,scene):port.controls(credential,qcId,scene,range.startDate(),range.endDate());
    }
    private String taskScene(String kind) {
        return switch(kind==null?"":kind) {case "一键起量"->"SMART_BOOST";case "素材追投"->"MATERIAL_ADD_BUDGET";default->throw new BizException(1402,"调控类型不正确");};
    }
    private JsonNode task(List<JsonNode> rows,String taskId) {
        if(taskId==null || !taskId.matches("[1-9][0-9]*|mock_task_[a-zA-Z0-9_-]+"))throw new BizException(1402,"调控任务ID格式不正确");
        return rows.stream().filter(row->taskId.equals(row.path("id").asText())).findFirst().orElseThrow(()->new BizException(1405,"当前计划下未找到此调控任务，请刷新列表"));
    }
    public ControlResult controlAction(TenantId tenant,PlanId id,String taskId,ControlTaskActionRequest request) {
        synchronized(store) {
            billing.assertWritable(tenant,Instant.now(),WriteAction.SAVE_PLAN);
            if(transfers!=null)transfers.assertIdle(tenant,id);
            String scene=taskScene(request.kind());String action=request.action();
            String operation=switch(action==null?"":action) {case "PAUSE"->"PAUSE";case "RESUME"->"ENABLE";case "END"->"DISABLE";case "DELETE"->"DELETE";default->throw new BizException(1402,"调控操作不正确");};
            AdPlan plan=require(tenant,id);editable(plan);var c=credentials.getUsableCredential(tenant,plan.accountId());String qcId=plan.qcPlanId().orElseThrow().value();
            JsonNode current=task(port.controls(c,qcId,scene),taskId);String status=current.path("task_status").asText();
            if(request.expectedStatus()==null || !status.equals(request.expectedStatus()))throw new BizException(1403,"任务状态已变化，请刷新列表后再操作");
            boolean allowed=switch(action) {case "PAUSE"->"PROCESSING".equals(status);case "RESUME"->"DISABLE".equals(status);
                case "END"->Set.of("PROCESSING","DISABLE","OFFLINE_BALANCE","ROI2_DISABLE","LIVE_ROOM_OFF").contains(status);
                case "DELETE"->Set.of("PROCESSING","DISABLE","OFFLINE_BALANCE","ROI2_DISABLE","LIVE_ROOM_OFF","FROZEN","OFFLINE_TIME","OFFLINE_BUDGET","MOCK").contains(status);default->false;};
            if(!allowed)throw new BizException(1403,"当前任务状态不支持此操作，请刷新列表");
            if("RESUME".equals(action)) {
                var detail=port.detail(c,qcId);
                if(!detail.hasNonNull("status"))throw new BizException(1406,"暂未确认当前投放计划状态，请同步核查后再继续调控");
                if(plan.status()!=PlanStatus.DELIVERING || detail.hasNonNull("status") && QianchuanPlanMapping.status(detail.path("status").asText())!=PlanStatus.DELIVERING)
                    throw new BizException(1403,"请先启用当前投放计划，再继续调控");
            }
            port.controlStatus(c,taskId,operation);
            String label=switch(action) {case "PAUSE"->"暂停调控";case "RESUME"->"继续调控";case "END"->"结束调控";default->"删除调控任务";};
            record(tenant,id,label+"："+current.path("name").asText(taskId),"任务ID："+taskId);
            return new ControlResult(taskId,"SUCCEEDED");
        }
    }
    public ControlTaskReport controlReport(TenantId tenant,PlanId id,String taskId,String kind,PlanStatisticsRange range,String granularity) {
        String scene=taskScene(kind);
        if(range==null)throw new BizException(1402,"请选择统计时间区间");
        if(!Set.of("HOUR","DAY").contains(granularity==null?"":granularity))throw new BizException(1402,"报表时间维度不正确");
        if("HOUR".equals(granularity) && java.time.temporal.ChronoUnit.DAYS.between(range.startDate(),range.endDate())>=7)throw new BizException(1402,"分时报表区间最长7天，请改用分日报表");
        AdPlan plan=require(tenant,id);if(plan.status()==PlanStatus.DELETED || plan.qcPlanId().isEmpty())throw new BizException(1405,"计划不存在或尚未发布");
        var c=credentials.getUsableCredential(tenant,plan.accountId());String qcId=plan.qcPlanId().orElseThrow().value();
        JsonNode current=task(port.controls(c,qcId,scene,range.startDate(),range.endDate()),taskId);
        var report=port.controlReport(c,qcId,taskId,scene,range.startDate(),range.endDate(),granularity);
        if(report.summary().has("dimensions") && !taskId.equals(PlanInsightsService.dimension(report.summary(),"assist_aid")))throw new BizException(1406,"千川任务报表ID不匹配");
        Map<String,BigDecimal> summary=taskMetrics(report.summary().has("metrics")?report.summary().path("metrics"):current.path("metrics"),report.metricFields());
        Set<String> times=new HashSet<>();List<ControlTaskPoint> trend=new ArrayList<>();
        for(JsonNode row:report.trend()) {
            if(!taskId.equals(PlanInsightsService.dimension(row,"assist_aid")))throw new BizException(1406,"千川任务趋势ID不匹配");
            String time=PlanInsightsService.reportTime(PlanInsightsService.dimension(row,report.timeDimension()),report.timeDimension(),range);
            if(!times.add(time))throw new BizException(1406,"千川任务趋势含重复时间，请重试");
            trend.add(new ControlTaskPoint(time,taskMetrics(row.path("metrics"),report.metricFields())));
        }
        trend.sort(Comparator.comparing(ControlTaskPoint::time));
        var metadata=json.createObjectNode();for(String field:List.of("id","name","scene","task_status","budget","duration","create_time"))if(current.has(field))metadata.set(field,current.get(field));
        return new ControlTaskReport(metadata,summary,List.copyOf(trend),granularity,Instant.now(),report.warning());
    }
    private Map<String,BigDecimal> taskMetrics(JsonNode values,Map<String,String> fields) {
        Map<String,BigDecimal> result=new LinkedHashMap<>();fields.forEach((key,field)->result.put(key,QianchuanPlanMapping.number(values,field)));return Collections.unmodifiableMap(result);
    }
    private void record(TenantId tenant,PlanId id,String label,String extra) {
        var principal=TenantContext.require();
        audit.record(new SuccessAuditCommand(tenant,principal.userId(),"商家",AuditActionType.PLAN,"PLAN",id.value(),label,
                RequestMeta.ip().orElse(null),principal.sessionId().orElse(null),RequestMeta.userAgent().orElse(null),
                json.createObjectNode().put("detail",extra).toString()));
    }
}
