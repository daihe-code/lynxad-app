package com.lynx.ad.modules.delivery.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.delivery.api.dto.PlanTransferRequest;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.infrastructure.adapter.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.*;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlanTransferService {
    public record Context(String planId,String currentQcPlanId,String targetMode,BigDecimal budget,BigDecimal roi2Goal,
                          boolean reuse,String counterpartQcPlanId,boolean available,String reason,String pendingKey,
                          String pendingStatus,String pendingError) {}
    public record Result(String planId,String qcPlanId,String status,String message) {}
    private final AdPlanRepository plans;
    private final PlanTransferStore store;
    private final CreationStore creations;
    private final PlanRemoteStore remote;
    private final PlanTransferGateway gateway;
    private final BillingTrialService billing;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public PlanTransferService(AdPlanRepository plans,PlanTransferStore store,CreationStore creations,PlanRemoteStore remote,
                               PlanTransferGateway gateway,BillingTrialService billing,ObjectMapper json,PlatformTransactionManager manager) {
        this.plans=plans;this.store=store;this.creations=creations;this.remote=remote;this.gateway=gateway;this.billing=billing;this.json=json;tx=new TransactionTemplate(manager);
    }
    public Context context(TenantId tenant,PlanId id) {
        var g=store.group(tenant,id);var current=require(tenant,id.value());
        SmartBidType mode=opposite(current.setting().bidType());String targetId=g==null?null:g.target(mode);
        var target=targetId==null?null:require(tenant,targetId);
        String reason=null;
        if(current.qcPlanId().isEmpty() || !editable(current.status()))reason="仅已发布且投放中或暂停的计划支持转投";
        else if(target==null && creations.publishedPayload(tenant.value(),g==null?id.value():g.root()).isEmpty())reason="缺少已发布的商品与素材配置，请在千川后台转投";
        Attempt a=g==null||g.requestKey()==null?null:store.attempt(tenant,g.requestKey()).orElse(null);
        if(g!=null&&g.requestKey()!=null)reason="转投正在处理或结果待核查";
        return new Context(id.value(),current.qcPlanId().map(QcPlanId::value).orElse(null),mode.oceanValue(),
                (target==null?current:target).setting().dailyBudget().amount(),target==null?null:target.setting().roi2Goal().map(RoiGoal::value).orElse(null),
                target!=null,target==null?null:target.qcPlanId().map(QcPlanId::value).orElse(null),reason==null,reason,a==null?null:a.key(),a==null?null:a.status(),a==null?null:a.error());
    }
    public Result transfer(TenantId tenant,PlanId id,PlanTransferRequest request) {
        synchronized(remote) {return transferLocked(tenant,id,request);}
    }
    /** Previously transferred plans switch only their enabled member, preserving platform settings. */
    public Result switchExisting(TenantId tenant,PlanId id,String requestKey,String expectedQcPlanId) {
        return transfer(tenant,id,new PlanTransferRequest(requestKey,expectedQcPlanId,null,null,null,true));
    }
    /** A normal ENABLE uses the same durable group lock and pause-before-enable workflow as transfer. */
    public Result enableExisting(TenantId tenant,PlanId id) {
        synchronized(remote) {
            var target=require(tenant,id.value());var g=store.group(tenant,id);
            String sourceId=g==null?null:g.target(opposite(target.setting().bidType()));
            if(sourceId==null)throw new BizException(1403,"未找到关联的另一种投放方式");
            var source=require(tenant,sourceId);
            return transferLocked(tenant,source.id(),new PlanTransferRequest(UUID.randomUUID().toString(),source.qcPlanId().orElseThrow().value(),null,null,null,true));
        }
    }
    private Result transferLocked(TenantId tenant,PlanId id,PlanTransferRequest request) {
        billing.assertWritable(tenant,Instant.now(),WriteAction.PUBLISH);validate(request);
        var group=store.group(tenant,id);String root=group==null?id.value():group.root();
        var prior=store.attempt(tenant,request.requestKey());
        if(prior.isPresent()) {
            var a=prior.get();if(!root.equals(a.root()) || !sameRequest(a.request(),request))throw new BizException(1404,"转投请求号已使用，请刷新后操作");
            return result(a);
        }
        store.assertIdle(tenant,id);
        var source=require(tenant,id.value());
        if(group!=null && !source.id().value().equals(group.target(source.setting().bidType())))
            throw new BizException(1404,"转投关联的投放方式不一致，请先核查计划");
        if(source.qcPlanId().isEmpty() || !source.qcPlanId().get().value().equals(request.expectedQcPlanId()) || !(editable(source.status()) || request.existingOnly() && enabled(source.status())))
            throw new BizException(1404,"当前计划已变化，请刷新后转投");
        SmartBidType mode=request.existingOnly()?opposite(source.setting().bidType()):SmartBidType.fromOcean(request.smartBidType());
        if(mode!=opposite(source.setting().bidType()))throw new BizException(1402,"请选择另一种投放方式");
        String targetId=group==null?null:group.target(mode);var target=targetId==null?null:require(tenant,targetId);
        if(request.existingOnly() && target==null)throw new BizException(1403,"尚无另一种投放方式的计划，请先在设置页面完成首次转投");
        if(target!=null && (!source.accountId().equals(target.accountId()) || !source.awemeId().equals(target.awemeId()) || target.qcPlanId().isEmpty()))
            throw new BizException(1404,"转投关联的账户信息不一致，请先核查计划");
        var credential=gateway.credential(source);
        var sourceDetail=gateway.detail(source);checkMode(sourceDetail,source.setting().bidType());
        var sourceStatus=status(sourceDetail);if(!(editable(sourceStatus) || request.existingOnly() && enabled(sourceStatus)))throw new BizException(1404,"平台计划状态已变化，请先同步计划");
        BigDecimal updateBudget=null,updateRoi=null;
        JsonNode targetDetail=null;
        if(target!=null) {
            var d=gateway.detail(target);checkMode(d,mode);
            if(status(d)!=PlanStatus.PAUSED && !(request.existingOnly() && enabled(status(d))))
                throw new BizException(1404,"待启用的计划状态已变化，请先核查千川计划");
            targetDetail=d;
            if(request.existingOnly()) {
                var ds=QianchuanPlanMapping.setting(d);
                request=new PlanTransferRequest(request.requestKey(),request.expectedQcPlanId(),mode.oceanValue(),QianchuanPlanMapping.number(ds,"budget"),
                        mode==SmartBidType.CUSTOM?QianchuanPlanMapping.number(ds,"roi2_goal"):null,true);
            } else {
                if(!equal(QianchuanPlanMapping.number(QianchuanPlanMapping.setting(d),"budget"),request.budget()))updateBudget=request.budget();
                if(mode==SmartBidType.CUSTOM && !equal(QianchuanPlanMapping.number(QianchuanPlanMapping.setting(d),"roi2_goal"),request.roi2Goal()))updateRoi=request.roi2Goal();
            }
        }
        var payload=target==null?payload(tenant,root,source,request):json.createObjectNode();
        if(targetId==null)targetId=UUID.randomUUID().toString();
        var g=store.ensure(source);
        Attempt a=new Attempt(tenant.value(),request.requestKey(),g.root(),source.id().value(),targetId,encode(request),payload.toString(),enabled(sourceStatus),
                target==null?null:target.qcPlanId().orElseThrow().value(),"RUNNING","PREPARED",null,Instant.now());
        store.claim(a,g.active());
        try {
            if(target!=null) {
                if(updateBudget!=null || updateRoi!=null) {
                    store.phase(tenant,a.key(),"UPDATING",target.qcPlanId().orElseThrow().value());
                    gateway.settings(credential,target.qcPlanId().orElseThrow().value(),updateBudget,updateRoi);
                }
                if(request.existingOnly())applyDetail(target,targetDetail);
                else target.updateSetting(setting(target,request));
                plans.save(target);
            }
            store.phase(tenant,a.key(),"PAUSING",target==null?null:target.qcPlanId().orElseThrow().value());
            // Sending DISABLE also for an already paused source gives an explicit acknowledgement.
            gateway.status(credential,source.qcPlanId().get().value(),false);confirmPaused(source);
            store.phase(tenant,a.key(),"PAUSED",null);
            if(target==null) {
                store.phase(tenant,a.key(),"CREATING",null);
                String qc=gateway.create(source,payload);
                // Save the receipt before any domain/cache updates, so recovery never creates again.
                store.phase(tenant,a.key(),"CREATED",qc);
                target=register(tenant,store.attempt(tenant,a.key()).orElseThrow(),source,request);
            }
            store.phase(tenant,a.key(),"ENABLING",target.qcPlanId().orElseThrow().value());
            // The opposite row may already be enabled. Do not toggle it or create another plan.
            if(targetDetail==null || !enabled(status(targetDetail)))gateway.status(credential,target.qcPlanId().orElseThrow().value(),true);
            store.phase(tenant,a.key(),"ENABLED",target.qcPlanId().orElseThrow().value());
            confirmEnabled(target);
            store.finish(tenant,a.key(),"SUCCEEDED",null);
        } catch(RuntimeException error) {
            var latest=store.attempt(tenant,a.key()).orElseThrow();
            if(error instanceof BizException b && b.code()!=1523 && Set.of("CREATING","UPDATING","PAUSING","ENABLING").contains(latest.phase())) {
                try {
                    store.phase(tenant,a.key(),"ROLLING_BACK",null);
                    // A rejected ENABLE must leave the target explicitly disabled before source restoration.
                    if(latest.qcId()!=null && ("ENABLING".equals(latest.phase()) || latest.sourceEnabled() && targetDetail!=null && enabled(status(targetDetail)))) {
                        gateway.status(credential,latest.qcId(),false);if(target!=null)confirmPaused(target);
                    }
                    if(latest.sourceEnabled()) {gateway.status(credential,source.qcPlanId().get().value(),true);confirmEnabled(source);}
                    store.finish(tenant,a.key(),"FAILED",b.getMessage()+ (latest.sourceEnabled()?"；原计划已恢复":"；原计划保持暂停"));
                }catch(RuntimeException rollback){store.finish(tenant,a.key(),"UNCERTAIN",b.getMessage()+"；恢复结果待核查，请核查转投结果");}
            } else store.finish(tenant,a.key(),"UNCERTAIN","转投结果待核查，请勿重复创建或启用计划"+(error instanceof BizException?"："+error.getMessage():""));
        }
        return result(store.attempt(tenant,a.key()).orElseThrow());
    }
    /** Recover receipts and inspect remote state. An absent search result is never proof of a rejected create. */
    public Result reconcile(TenantId tenant,PlanId id) {
        synchronized(remote) {
            billing.assertWritable(tenant,Instant.now(),WriteAction.PUBLISH);
            var g=store.group(tenant,id);if(g==null||g.requestKey()==null)return new Result(g==null?id.value():g.active(),require(tenant,g==null?id.value():g.active()).qcPlanId().map(QcPlanId::value).orElse(null),"IDLE","当前没有待核查的转投");
            var a=store.attempt(tenant,g.requestKey()).orElseThrow();
            if("RUNNING".equals(a.status()) && a.updatedAt().isAfter(Instant.now().minusSeconds(180)))return result(a);
            var source=require(tenant,a.source());var req=decode(a.request());var credential=gateway.credential(source);
            if(a.qcId()==null && "CREATING".equals(a.phase())) {
                var candidates=gateway.findCreated(credential,SmartBidType.fromOcean(req.smartBidType()),parse(a.payload()).path("name").asText());
                if(candidates.size()!=1)return pending(tenant,a,"未确认新计划的唯一创建结果，请稍后核查或在千川后台确认；不会重复创建");
                store.phase(tenant,a.key(),"CREATED",candidates.getFirst().qcPlanId());a=store.attempt(tenant,a.key()).orElseThrow();
            }
            if(a.qcId()==null) {
                // Only phases preceding CREATE are safe to roll back without an unknown target.
                var d=gateway.detail(source);if(a.sourceEnabled() && status(d)==PlanStatus.PAUSED)gateway.status(credential,source.qcPlanId().orElseThrow().value(),true);
                refresh(source);if(a.sourceEnabled()&&!enabled(source.status()))return pending(tenant,a,"原计划恢复投放的状态尚未确认，请稍后核查");
                store.finish(tenant,a.key(),"FAILED","转投未创建新计划，原计划状态已恢复");return result(store.attempt(tenant,a.key()).orElseThrow());
            }
            var target=register(tenant,a,source,req);var td=gateway.detail(target);var sd=gateway.detail(source);checkMode(td,SmartBidType.fromOcean(req.smartBidType()));
            if("ROLLING_BACK".equals(a.phase())) {
                gateway.status(credential,a.qcId(),false);confirmPaused(target);
                if(a.sourceEnabled())gateway.status(credential,source.qcPlanId().orElseThrow().value(),true);
                refresh(source);if(a.sourceEnabled()&&!enabled(source.status()))return pending(tenant,a,"原计划恢复投放的状态尚未确认，请稍后核查");
                store.finish(tenant,a.key(),"FAILED","转投未完成，原计划状态已恢复");return result(store.attempt(tenant,a.key()).orElseThrow());
            }
            if("UPDATING".equals(a.phase()) && status(sd)!=PlanStatus.PAUSED && status(td)==PlanStatus.PAUSED) {
                applyDetail(target,td);plans.save(target);remote.detail(tenant,target.id().value(),td);
                store.finish(tenant,a.key(),"FAILED","转投未切换计划，原计划仍在使用；目标计划设置已核查");return result(store.attempt(tenant,a.key()).orElseThrow());
            }
            if(status(sd)!=PlanStatus.PAUSED)return pending(tenant,a,"原计划未暂停，请在千川后台核查两套计划的启停状态");
            if(status(td)==PlanStatus.PAUSED) {
                // Recovery proceeds only when requested settings are confirmed, never by resending CREATE.
                var ds=QianchuanPlanMapping.setting(td);
                if(!req.existingOnly() && (!equal(QianchuanPlanMapping.number(ds,"budget"),req.budget()) || (SmartBidType.fromOcean(req.smartBidType())==SmartBidType.CUSTOM && !equal(QianchuanPlanMapping.number(ds,"roi2_goal"),req.roi2Goal()))))
                    return pending(tenant,a,"目标计划的预算或ROI尚未确认，请核查千川设置");
                store.phase(tenant,a.key(),"ENABLING",a.qcId());gateway.status(credential,a.qcId(),true);td=gateway.detail(target);
            }
            if(status(td)!=PlanStatus.DELIVERING && status(td)!=PlanStatus.PENDING_AUDIT)return pending(tenant,a,"目标计划状态尚未确认，请稍后核查");
            applyDetail(target,td);plans.save(target);remote.detail(tenant,target.id().value(),td);paused(source);
            store.finish(tenant,a.key(),"SUCCEEDED",null);return result(store.attempt(tenant,a.key()).orElseThrow());
        }
    }
    private Result pending(TenantId t,Attempt a,String message){store.finish(t,a.key(),"UNCERTAIN",message);return result(store.attempt(t,a.key()).orElseThrow());}
    private static boolean enabled(PlanStatus status){return status==PlanStatus.DELIVERING || status==PlanStatus.PENDING_AUDIT;}
    private void confirmPaused(AdPlan plan) {
        refresh(plan);
        if(plan.status()!=PlanStatus.PAUSED)throw new BizException(1523,"另一种投放方式尚未确认暂停，暂不启用目标计划，请核查转投结果");
    }
    private void confirmEnabled(AdPlan plan) {
        refresh(plan);
        if(!enabled(plan.status()))throw new BizException(1523,"目标计划启用状态待核查");
    }
    private AdPlan register(TenantId t,Attempt a,AdPlan source,PlanTransferRequest req) {
        var existing=plans.findById(t,new PlanId(a.target()));
        if(existing.isPresent()) {store.member(t,a.root(),SmartBidType.fromOcean(req.smartBidType()),a.target());return existing.get();}
        var payload=parse(a.payload());
        String targetId=remote.findId(t,source.accountId(),a.qcId()).orElse(a.target());
        if(!targetId.equals(a.target())) {
            var adopted=require(t,targetId);
            if(!source.accountId().equals(adopted.accountId()) || adopted.setting().bidType()!=SmartBidType.fromOcean(req.smartBidType()))throw new BizException(1523,"平台计划关联不一致，请核查转投记录");
            store.adoptTarget(t,a.key(),targetId);store.member(t,a.root(),adopted.setting().bidType(),targetId);return adopted;
        }
        var target=new AdPlan(new PlanId(a.target()),t,source.accountId(),source.awemeId(),PlanName.fromPlatform(payload.path("name").asText(source.name().text())),
                PlanStatus.PENDING_AUDIT,source.source(),setting(source,req),source.productIds(),new QcPlanId(a.qcId()),"PENDING_AUDIT",null,1,null,source.bindings());
        tx.executeWithoutResult(s->{plans.save(target);remote.save(t,target.accountId(),a.qcId(),target.id().value(),payload);store.member(t,a.root(),target.setting().bidType(),target.id().value());});
        return target;
    }
    private ObjectNode payload(TenantId tenant,String root,AdPlan source,PlanTransferRequest request) {
        var p=creations.publishedPayload(tenant.value(),root).orElseThrow(()->new BizException(1403,"缺少已发布的商品与素材配置，请在千川后台转投"));
        if(!p.isObject() || !p.path("multi_product_creative_list").isArray() || p.path("multi_product_creative_list").isEmpty() || !p.path("aweme_id").asText().equals(source.awemeId().value()))
            throw new BizException(1403,"已发布素材配置不完整，无法转投");
        var result=(ObjectNode)p.deepCopy();result.remove("ad_id");
        // Published payloads are audit snapshots. Recreated mode members must respect later shared product removals.
        var snapshots=creations.productsByPlan(tenant.value(),List.of(root)).get(root);
        if(snapshots!=null) {
            var allowed=snapshots.stream().map(com.lynx.ad.modules.delivery.domain.model.CreationState.Product::qcProductId).collect(java.util.stream.Collectors.toSet());
            var creative=result.putArray("multi_product_creative_list");for(var item:p.path("multi_product_creative_list"))if(allowed.contains(item.path("product_id").asText()))creative.add(item);
            var ids=result.putArray("product_ids");for(var item:p.path("product_ids"))if(allowed.contains(item.asText()))ids.add(item);
            if(creative.isEmpty())throw new BizException(1403,"当前计划没有可复用的商品创意，请先核查素材");
        }
        String suffix="_转投_"+(request.smartBidType().endsWith("CUSTOM")?"控成本":"放量")+"_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String base=source.name().text();while((base+suffix).getBytes(StandardCharsets.UTF_8).length>100)base=base.substring(0,base.offsetByCodePoints(base.length(),-1));
        result.put("name",base+suffix);
        ObjectNode ds=result.withObject("/delivery_setting");ds.put("smart_bid_type",request.smartBidType());ds.put("budget",request.budget());
        ds.remove(List.of("roi2_goal","estimate_roi_goal","min_estimate_roi_goal","max_estimate_roi_goal","min_estimate_convert","estimate_convert"));
        if(request.smartBidType().endsWith("CUSTOM"))ds.put("roi2_goal",request.roi2Goal());
        else {
            var summary=creations.summariesByPlan(tenant.value(),List.of(root)).get(root);
            if(summary!=null && summary.recommendations()!=null)for(var tier:summary.recommendations().path("budget").path("estimate_trans_cost_list")) {
                if(!equal(QianchuanPlanMapping.number(tier,"budget"),request.budget()))continue;
                for(String key:List.of("estimate_roi","min_estimate_roi","min_estimate_convert","estimate_convert")) {
                    var value=QianchuanPlanMapping.number(tier,key);if(value!=null && value.signum()>=0)ds.put(key.endsWith("roi")?key+"_goal":key,value);
                }break;
            }
        }
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var schedule=source.setting().schedule();
        if(schedule.endDate()!=null && schedule.endDate().isBefore(today))throw new BizException(1403,"计划已到期，无法转投");
        if("SCHEDULE_START_END".equals(ds.path("video_schedule_type").asText()))ds.put("start_time",today.toString());
        return result;
    }
    private DeliverySetting setting(AdPlan p,PlanTransferRequest r) {var mode=SmartBidType.fromOcean(r.smartBidType());return new DeliverySetting(mode,Money.ofYuan(r.budget()),mode==SmartBidType.CUSTOM?Optional.of(new RoiGoal(r.roi2Goal())):Optional.empty(),p.setting().schedule(),p.setting().audience());}
    private void paused(AdPlan p){p.apply(PlanStatus.PAUSED);p.syncPlatformStatus("DISABLE");plans.save(p);}
    private void refresh(AdPlan p){var d=gateway.detail(p);applyDetail(p,d);plans.save(p);remote.detail(p.tenantId(),p.id().value(),d);}
    private void applyDetail(AdPlan p,JsonNode d){
        p.apply(status(d));p.syncPlatformStatus(p.status()==PlanStatus.PAUSED?"DISABLE":QianchuanPlanMapping.text(QianchuanPlanMapping.info(d),"status"));
        var ds=QianchuanPlanMapping.setting(d);BigDecimal budget=QianchuanPlanMapping.number(ds,"budget");
        if(budget!=null) {
            SmartBidType mode=ds.hasNonNull("smart_bid_type")?SmartBidType.fromOcean(ds.path("smart_bid_type").asText()):p.setting().bidType();
            p.updateSetting(new DeliverySetting(mode,Money.ofYuan(budget),mode==SmartBidType.CUSTOM?Optional.ofNullable(QianchuanPlanMapping.number(ds,"roi2_goal")).map(RoiGoal::new):Optional.empty(),p.setting().schedule(),p.setting().audience()));
        }
    }
    private static PlanStatus status(JsonNode d){var n=QianchuanPlanMapping.info(d);if("DISABLE".equals(n.path("opt_status").asText(d.path("opt_status").asText())))return PlanStatus.PAUSED;return QianchuanPlanMapping.status(QianchuanPlanMapping.text(n,"status"));}
    private static void checkMode(JsonNode d,SmartBidType m){if(!m.oceanValue().equals(QianchuanPlanMapping.setting(d).path("smart_bid_type").asText()))throw new BizException(1404,"平台投放方式已变化或未返回，请同步核查");}
    private AdPlan require(TenantId t,String id){return plans.findById(t,new PlanId(id)).orElseThrow(()->new BizException(1405,"计划不存在"));}
    private static boolean editable(PlanStatus s){return s==PlanStatus.DELIVERING||s==PlanStatus.PAUSED;}
    private static SmartBidType opposite(SmartBidType m){return m==SmartBidType.CUSTOM?SmartBidType.CONSERVATIVE:SmartBidType.CUSTOM;}
    private static boolean equal(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
    private boolean sameRequest(String saved,PlanTransferRequest req){var old=decode(saved);return old.existingOnly()==req.existingOnly()&&Objects.equals(old.expectedQcPlanId(),req.expectedQcPlanId())&&(req.existingOnly()||(Objects.equals(old.smartBidType(),req.smartBidType())&&equal(old.budget(),req.budget())&&equal(old.roi2Goal(),req.roi2Goal())));}
    private Result result(Attempt a){return new Result("SUCCEEDED".equals(a.status())?a.target():a.source(),a.qcId(),a.status(),"SUCCEEDED".equals(a.status())?"转投成功，原计划已暂停":a.error()==null?"转投正在处理，请稍后核查":a.error());}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode parse(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private PlanTransferRequest decode(String value){try{return json.readValue(value,PlanTransferRequest.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void validate(PlanTransferRequest r){
        if(r==null || r.requestKey()==null || !r.requestKey().matches("[a-zA-Z0-9_-]{8,64}") || r.expectedQcPlanId()==null || r.expectedQcPlanId().isBlank())throw new BizException(1402,"转投参数不完整");
        if(r.existingOnly())return;
        if(!Set.of("SMART_BID_CUSTOM","SMART_BID_CONSERVATIVE").contains(r.smartBidType()==null?"":r.smartBidType()))throw new BizException(1402,"转投参数不完整");
        if(r.budget()==null || r.budget().scale()>2 || r.budget().signum()<=0 || r.budget().compareTo(new BigDecimal("100000"))>0)throw new BizException(1402,"日预算须大于0且不超过100,000元，最多两位小数");
        if(r.smartBidType().endsWith("CUSTOM") && (r.roi2Goal()==null || r.roi2Goal().scale()>2 || r.roi2Goal().compareTo(new BigDecimal("0.1"))<0 || r.roi2Goal().compareTo(new BigDecimal("50"))>0))throw new BizException(1402,"控成本投放ROI须为0.1–50，最多两位小数");
        if(r.smartBidType().endsWith("CONSERVATIVE") && r.roi2Goal()!=null)throw new BizException(1402,"放量投放不设置固定ROI目标");
    }
}
