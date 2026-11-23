package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;
import com.lynx.ad.modules.delivery.domain.service.PlanSchedule;

import com.fasterxml.jackson.databind.*;
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
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlanTransferService {
    private static final String PRODUCT_TRANSFER_BLOCKED = "该商品已有投放计划，暂不支持切换投放方式。";
    private static final String FIRST_TRANSFER_UNAVAILABLE = "首次转投暂不可提交。";
    public record Context(String planId,String currentQcPlanId,String targetMode,BigDecimal budget,BigDecimal roi2Goal,
                          boolean reuse,String counterpartQcPlanId,boolean available,boolean canPrepare,String reason,String pendingKey,
                          String pendingStatus,String pendingError,String targetName,String counterpartPlanId) {}
    public record Result(String planId,String qcPlanId,String status,String message,boolean platformTransferRequired) {
        public Result(String planId,String qcPlanId,String status,String message) {
            this(planId,qcPlanId,status,message,false);
        }
    }
    private final AdPlanRepository plans;
    private final PlanTransferStore store;
    private final PlanRemoteStore remote;
    private final PlanTransferGateway gateway;
    private final BillingTrialService billing;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public PlanTransferService(AdPlanRepository plans,PlanTransferStore store,PlanRemoteStore remote,
                               PlanTransferGateway gateway,BillingTrialService billing,ObjectMapper json,PlatformTransactionManager manager) {
        this.plans=plans;this.store=store;this.remote=remote;this.gateway=gateway;this.billing=billing;this.json=json;tx=new TransactionTemplate(manager);
    }
    public Context context(TenantId tenant,PlanId id) {
        var g=store.group(tenant,id);var current=require(tenant,id.value());
        SmartBidType mode=opposite(current.setting().bidType());String targetId=g==null?null:g.target(mode);
        var target=targetId==null?null:require(tenant,targetId);
        String reason=null;
        if(current.qcPlanId().isEmpty() || !editable(current.status()))reason="仅已发布且投放中或暂停的计划支持转投";
        Attempt a=g==null||g.requestKey()==null?null:store.attempt(tenant,g.requestKey()).orElse(null);
        if(g!=null&&g.requestKey()!=null)reason=g.requestKey().startsWith("settings:")?"计划修改正在处理或结果待核查，请先核查修改结果":"转投正在处理或结果待核查";
        boolean available=reason==null && target!=null;
        boolean canPrepare=reason==null && target==null;
        return new Context(id.value(),current.qcPlanId().map(QcPlanId::value).orElse(null),mode.oceanValue(),
                target==null?null:target.setting().dailyBudget().amount(),target==null?null:target.setting().roi2Goal().map(RoiGoal::value).orElse(null),
                target!=null,target==null?null:target.qcPlanId().map(QcPlanId::value).orElse(null),available,canPrepare,
                canPrepare?FIRST_TRANSFER_UNAVAILABLE:reason,a==null?null:a.key(),a==null?null:a.status(),a==null?null:result(a).message(),
                target==null?transferName(current.name().text(),mode):target.name().text(),targetId);
    }
    public JsonNode recommendations(TenantId tenant, PlanId id) {
        var context = context(tenant, id);
        if (!context.available() && !context.canPrepare()) throw new BizException(1403, context.reason());
        var mode = SmartBidType.fromOcean(context.targetMode());
        var plan = require(tenant, context.reuse() ? context.counterpartPlanId() : id.value());
        var detail = gateway.detail(plan);
        checkMode(detail, plan.setting().bidType());
        var ids = productIds(detail);
        if (ids.isEmpty()) throw new BizException(1403, "暂时无法获取计划商品的推荐预算");
        return gateway.recommendations(plan, List.copyOf(ids), mode);
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
        String targetId=group==null?null:group.target(mode);
        if(targetId==null)throw new BizException(1403,FIRST_TRANSFER_UNAVAILABLE);
        var target=require(tenant,targetId);
        if(!source.accountId().equals(target.accountId()) || !source.awemeId().equals(target.awemeId()) || target.qcPlanId().isEmpty())
            throw new BizException(1404,"转投关联的账户信息不一致，请先核查计划");
        String targetQcId=target.qcPlanId().orElseThrow().value();
        var credential=gateway.credential(source);
        var sourceDetail=gateway.detail(source);checkMode(sourceDetail,source.setting().bidType());
        transferSchedule(source,request);
        var sourceStatus=status(sourceDetail);if(!(editable(sourceStatus) || request.existingOnly() && enabled(sourceStatus)))throw new BizException(1404,"平台计划状态已变化，请先同步计划");
        BigDecimal updateBudget=null,updateRoi=null;
        var targetDetail=gateway.detail(target);checkMode(targetDetail,mode);
        var targetStatus=status(targetDetail);
        if(targetStatus!=PlanStatus.PAUSED && !(request.existingOnly() && enabled(targetStatus)))
            throw new BizException(1404,"待启用的计划状态已变化，请刷新后重试");
        var targetSetting=QianchuanPlanMapping.setting(targetDetail);
        if(request.existingOnly()) {
            request=new PlanTransferRequest(request.requestKey(),request.expectedQcPlanId(),mode.oceanValue(),QianchuanPlanMapping.number(targetSetting,"budget"),
                    mode==SmartBidType.CUSTOM?QianchuanPlanMapping.number(targetSetting,"roi2_goal"):null,true);
        } else {
            if(!equal(QianchuanPlanMapping.number(targetSetting,"budget"),request.budget()))updateBudget=request.budget();
            if(mode==SmartBidType.CUSTOM && !equal(QianchuanPlanMapping.number(targetSetting,"roi2_goal"),request.roi2Goal()))updateRoi=request.roi2Goal();
        }
        String existingName = QianchuanPlanMapping.info(targetDetail).path("name").asText(target.name().text());
        String targetName = null;
        if (!request.existingOnly()) {
            targetName = transferName(request.name() == null ? existingName : new PlanName(request.name()).text(),mode);
        }
        boolean renameTarget = targetName != null && !targetName.equals(existingName);
        if(renameTarget && plans.existsUnfinishedName(tenant,source.awemeId(),PlanName.fromPlatform(targetName),Optional.of(target.id())))
            throw new BizException(1401,"同一抖音号存在同名计划，请修改转投计划名称");
        var payload=json.createObjectNode();
        if (targetName != null) payload.put("name",targetName);
        Attempt a=new Attempt(tenant.value(),request.requestKey(),root,source.id().value(),targetId,encode(request),payload.toString(),
                enabled(sourceStatus),targetQcId,"RUNNING","PREPARED",null,Instant.now());
        store.claim(a,group.active());
        try {
            if(updateBudget!=null || updateRoi!=null || renameTarget || request.scheduleType()!=null) {
                store.phase(tenant,a.key(),"UPDATING",targetQcId);
                if(updateBudget!=null || updateRoi!=null)gateway.settings(credential,targetQcId,updateBudget,updateRoi);
                if(renameTarget || request.scheduleType()!=null)
                    gateway.details(credential,targetQcId,renameTarget?targetName:null,request.scheduleType(),request.endDate());
                targetDetail=gateway.detail(target);
                if(!matches(targetDetail,request,targetName))throw new BizException(1523,"目标计划设置尚未确认，请核查后继续转投");
            }
            if(request.existingOnly())applyDetail(target,targetDetail);
            else {target.updateSetting(setting(target,request));if(renameTarget)target.rename(PlanName.fromPlatform(targetName));}
            plans.save(target);
            store.phase(tenant,a.key(),"PAUSING",targetQcId);
            // Sending DISABLE also for an already paused source gives an explicit acknowledgement.
            gateway.status(credential,source.qcPlanId().get().value(),false);confirmPaused(source);
            store.phase(tenant,a.key(),"PAUSED",null);
            store.phase(tenant,a.key(),"ENABLING",targetQcId);
            if(!enabled(status(targetDetail)))gateway.status(credential,targetQcId,true);
            store.phase(tenant,a.key(),"ENABLED",targetQcId);
            confirmEnabled(target);
            if(!request.existingOnly() && !matches(gateway.detail(target),request,targetName))throw new BizException(1523,"计划修改结果尚未确认，请核查后继续");
            store.finish(tenant,a.key(),"SUCCEEDED",null);
        } catch(RuntimeException error) {
            var latest=store.attempt(tenant,a.key()).orElseThrow();
            if(error instanceof BizException b && b.code()!=1523 && Set.of("UPDATING","PAUSING","ENABLING").contains(latest.phase())) {
                try {
                    store.phase(tenant,a.key(),"ROLLING_BACK",null);
                    // A rejected ENABLE must leave the target explicitly disabled before source restoration.
                    if("ENABLING".equals(latest.phase()) || latest.sourceEnabled() && enabled(status(targetDetail))) {
                        gateway.status(credential,targetQcId,false);confirmPaused(target);
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
                String message = requiresPlatformTransfer(a.error()) ? "千川拒绝创建目标计划，商品全域投放互斥（错误码 40000）"
                        : "转投未创建新计划";
                message += a.sourceEnabled() ? "，原计划状态已恢复" : "，原计划保持暂停";
                store.finish(tenant,a.key(),"FAILED",message);return result(store.attempt(tenant,a.key()).orElseThrow());
            }
            var target=register(tenant,a,source,req);var td=gateway.detail(target);var sd=gateway.detail(source);checkMode(td,SmartBidType.fromOcean(req.smartBidType()));
            String targetName = parse(a.payload()).path("name").asText(req.name());
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
                if(!req.existingOnly() && !matches(td,req,targetName))
                    return pending(tenant,a,"计划修改值尚未全部确认，请核查后继续");
                store.phase(tenant,a.key(),"ENABLING",a.qcId());gateway.status(credential,a.qcId(),true);td=gateway.detail(target);
            }
            if(status(td)!=PlanStatus.DELIVERING && status(td)!=PlanStatus.PENDING_AUDIT)return pending(tenant,a,"目标计划状态尚未确认，请稍后核查");
            if(!req.existingOnly() && !matches(td,req,targetName))return pending(tenant,a,"计划修改值尚未全部确认，请核查后继续");
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
        if(existing.isPresent()) {restoreCreatedOrigin(existing.get(),a);store.member(t,a.root(),SmartBidType.fromOcean(req.smartBidType()),a.target());return existing.get();}
        var payload=parse(a.payload());
        String targetId=remote.findId(t,source.accountId(),a.qcId()).orElse(a.target());
        if(!targetId.equals(a.target())) {
            var adopted=require(t,targetId);
            if(!source.accountId().equals(adopted.accountId()) || adopted.setting().bidType()!=SmartBidType.fromOcean(req.smartBidType()))throw new BizException(1523,"平台计划关联不一致，请核查转投记录");
            restoreCreatedOrigin(adopted,a);store.adoptTarget(t,a.key(),targetId);store.member(t,a.root(),adopted.setting().bidType(),targetId);return adopted;
        }
        var target=new AdPlan(new PlanId(a.target()),t,source.accountId(),source.awemeId(),PlanName.fromPlatform(payload.path("name").asText(source.name().text())),
                PlanStatus.PENDING_AUDIT,PlanSource.LINGTOU,setting(source,req),
                java.util.stream.StreamSupport.stream(payload.path("product_ids").spliterator(),false).map(v->new QcProductId(v.asText())).toList(),
                new QcPlanId(a.qcId()),"PENDING_AUDIT",null,1,null,source.bindings());
        tx.executeWithoutResult(s->{plans.save(target);remote.save(t,target.accountId(),a.qcId(),target.id().value(),payload);store.member(t,a.root(),target.setting().bidType(),target.id().value());});
        return target;
    }
    private void restoreCreatedOrigin(AdPlan plan,Attempt attempt) {
        var groups=parse(attempt.payload()).path("multi_product_creative_list");
        if(plan.source()==PlanSource.EXTERNAL && plan.qcPlanId().map(q->q.value().equals(attempt.qcId())).orElse(false)
                && groups.isArray() && !groups.isEmpty()) {plan.restoreLingtouSource();plans.save(plan);}
    }
    private DeliverySetting setting(AdPlan p,PlanTransferRequest r) {var mode=SmartBidType.fromOcean(r.smartBidType());return new DeliverySetting(mode,Money.ofYuan(r.budget()),mode==SmartBidType.CUSTOM?Optional.of(new RoiGoal(r.roi2Goal())):Optional.empty(),transferSchedule(p,r),p.setting().audience());}
    private static Schedule transferSchedule(AdPlan p,PlanTransferRequest r) {
        var old=p.setting().schedule();var type=r.scheduleType()==null?old.type():ScheduleType.valueOf(r.scheduleType());
        var end=type==ScheduleType.FROM_NOW?null:r.scheduleType()==null?old.endDate():r.endDate();
        if(r.scheduleType()!=null && type==ScheduleType.DATE_RANGE && (end==null || end.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))) || old.startDate()!=null && end.isBefore(old.startDate())))throw new BizException(1402,"结束日期不得早于今天或原计划开始日期");
        return new Schedule(type,old.startDate(),end);
    }
    private static boolean matches(JsonNode detail,PlanTransferRequest r,String targetName) {
        var ds=QianchuanPlanMapping.setting(detail);
        return r.smartBidType().equals(ds.path("smart_bid_type").asText())
            && equal(QianchuanPlanMapping.number(ds,"budget"),r.budget()) && (!r.smartBidType().endsWith("CUSTOM") || equal(QianchuanPlanMapping.number(ds,"roi2_goal"),r.roi2Goal()))
            && (targetName==null || targetName.equals(QianchuanPlanMapping.info(detail).path("name").asText()))
            && (r.scheduleType()==null || PlanSchedule.complete(ds) && r.scheduleType().equals(PlanSchedule.type(ds).name()) && ("FROM_NOW".equals(r.scheduleType()) || r.endDate().equals(QianchuanPlanMapping.date(QianchuanPlanMapping.text(ds,"end_time")))));
    }
    private static String transferName(String base,SmartBidType mode) {
        if (base.contains("转投")) return base;
        String suffix = mode == SmartBidType.CUSTOM ? "_转投控成本" : "_转投放量";
        int end = Math.min(base.length(),DeliveryInputPolicy.RULES.nameMaxLength() - suffix.length());
        if (end < base.length() && Character.isHighSurrogate(base.charAt(end - 1))) end--;
        return base.substring(0,end).stripTrailing() + suffix;
    }
    private void paused(AdPlan p){p.apply(PlanStatus.PAUSED);p.syncPlatformStatus("DISABLE");plans.save(p);}
    private void refresh(AdPlan p){var d=gateway.detail(p);applyDetail(p,d);plans.save(p);remote.detail(p.tenantId(),p.id().value(),d);}
    private void applyDetail(AdPlan p,JsonNode d){
        p.apply(status(d));p.syncPlatformStatus(p.status()==PlanStatus.PAUSED?"DISABLE":QianchuanPlanMapping.text(QianchuanPlanMapping.info(d),"status"));
        if(QianchuanPlanMapping.info(d).hasNonNull("name"))p.rename(PlanName.fromPlatform(QianchuanPlanMapping.info(d).path("name").asText()));
        var ds=QianchuanPlanMapping.setting(d);BigDecimal budget=QianchuanPlanMapping.number(ds,"budget");
        if(budget!=null) {
            SmartBidType mode=ds.hasNonNull("smart_bid_type")?SmartBidType.fromOcean(ds.path("smart_bid_type").asText()):p.setting().bidType();
            p.updateSetting(new DeliverySetting(mode,Money.ofYuan(budget),mode==SmartBidType.CUSTOM?Optional.ofNullable(QianchuanPlanMapping.number(ds,"roi2_goal")).map(RoiGoal::new):Optional.empty(),PlanSchedule.read(ds,p.setting().schedule()),p.setting().audience()));
        }
    }
    private static PlanStatus status(JsonNode d){var n=QianchuanPlanMapping.info(d);if("DISABLE".equals(n.path("opt_status").asText(d.path("opt_status").asText())))return PlanStatus.PAUSED;return QianchuanPlanMapping.status(QianchuanPlanMapping.text(n,"status"));}
    private static void checkMode(JsonNode d,SmartBidType m){if(!m.oceanValue().equals(QianchuanPlanMapping.setting(d).path("smart_bid_type").asText()))throw new BizException(1404,"平台投放方式已变化或未返回，请同步核查");}
    private AdPlan require(TenantId t,String id){return plans.findById(t,new PlanId(id)).orElseThrow(()->new BizException(1405,"计划不存在"));}
    private static boolean editable(PlanStatus s){return s==PlanStatus.DELIVERING||s==PlanStatus.PAUSED;}
    private static SmartBidType opposite(SmartBidType m){return m==SmartBidType.CUSTOM?SmartBidType.CONSERVATIVE:SmartBidType.CUSTOM;}
    private static boolean equal(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}
    private boolean sameRequest(String saved,PlanTransferRequest req){var old=decode(saved);return old.existingOnly()==req.existingOnly()&&Objects.equals(old.expectedQcPlanId(),req.expectedQcPlanId())&&(req.existingOnly()||(Objects.equals(old.smartBidType(),req.smartBidType())&&equal(old.budget(),req.budget())&&equal(old.roi2Goal(),req.roi2Goal())&&Objects.equals(old.name(),req.name())&&Objects.equals(old.scheduleType(),req.scheduleType())&&Objects.equals(old.endDate(),req.endDate())));}
    private Result result(Attempt a) {
        boolean succeeded = "SUCCEEDED".equals(a.status());
        boolean productConflict = a.qcId() == null && requiresPlatformTransfer(a.error());
        boolean platformTransfer = productConflict && "FAILED".equals(a.status());
        String message;
        if (succeeded) message = "转投成功，原计划已暂停";
        else if (platformTransfer) message = PRODUCT_TRANSFER_BLOCKED + (a.sourceEnabled() ? "原计划状态已恢复。" : "原计划保持暂停。");
        else if (productConflict) message = "转投结果待核查，请稍后查询。";
        else message = a.error() == null ? "转投正在处理，请稍后核查" : a.error();
        return new Result(succeeded ? a.target() : a.source(), a.qcId(), a.status(), message, platformTransfer);
    }
    private static boolean requiresPlatformTransfer(String error) {
        return error != null && error.contains("错误码 40000") && error.contains("商品全域") && error.contains("互斥");
    }
    private static Set<String> productIds(JsonNode detail) {
        Set<String> ids = new TreeSet<>();
        for (var group : detail.path("multi_product_creative_list")) {
            if (group.hasNonNull("product_id")) ids.add(group.path("product_id").asText());
        }
        if (ids.isEmpty()) for (var id : detail.path("product_ids")) ids.add(id.asText());
        return ids;
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode parse(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private PlanTransferRequest decode(String value){try{return json.readValue(value,PlanTransferRequest.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void validate(PlanTransferRequest r){
        if(r==null || r.requestKey()==null || !r.requestKey().matches("[a-zA-Z0-9_-]{8,64}") || r.expectedQcPlanId()==null || r.expectedQcPlanId().isBlank())throw new BizException(1402,"转投参数不完整");
        if(r.existingOnly())return;
        if(r.name()!=null)new PlanName(r.name());
        if(r.scheduleType()!=null && !Set.of("FROM_NOW","DATE_RANGE").contains(r.scheduleType()))throw new BizException(1402,"投放日期类型不正确");
        if(r.scheduleType()==null && r.endDate()!=null || "FROM_NOW".equals(r.scheduleType()) && r.endDate()!=null)throw new BizException(1402,"投放日期参数不正确");
        if(!Set.of("SMART_BID_CUSTOM","SMART_BID_CONSERVATIVE").contains(r.smartBidType()==null?"":r.smartBidType()))throw new BizException(1402,"转投参数不完整");
        DeliveryInputPolicy.settings(DeliveryInputPolicy.mode(r.smartBidType()),r.budget(),r.roi2Goal(),DeliveryInputPolicy.RULES.creation());
    }
}
