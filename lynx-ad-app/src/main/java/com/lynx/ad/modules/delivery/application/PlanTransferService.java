package com.lynx.ad.modules.delivery.application;

import com.lynx.ad.modules.delivery.domain.service.CreationRecommendationPolicy;
import com.lynx.ad.modules.delivery.domain.service.DeliveryInputPolicy;
import com.lynx.ad.modules.delivery.domain.service.PlanSchedule;
import com.lynx.ad.modules.delivery.domain.service.PlanTransferNames;
import com.lynx.ad.modules.delivery.domain.service.PlanEditVersion;

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
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlanTransferService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(PlanTransferService.class);
    private static final String PRODUCT_TRANSFER_BLOCKED = "该商品已有投放计划，暂不支持切换投放方式。";
    private static final List<String> BUDGET_ESTIMATES=List.of("min_estimate_convert","estimate_convert","estimate_roi_goal","min_estimate_roi_goal");
    public record Context(String planId,String currentQcPlanId,String targetMode,BigDecimal budget,BigDecimal roi2Goal,
                          boolean reuse,String counterpartQcPlanId,boolean available,String reason,String pendingKey,
                          String pendingStatus,String pendingError,String targetName,String counterpartPlanId,boolean requiresResume) {}
    public record Result(String planId,String qcPlanId,String status,String message,boolean platformTransferRequired) {
        public Result(String planId,String qcPlanId,String status,String message) {
            this(planId,qcPlanId,status,message,false);
        }
    }
    private final AdPlanRepository plans;
    private final PlanTransferStore store;
    private final CreationStore creations;
    private final PlanRemoteStore remote;
    private final PlanTransferGateway gateway;
    private final BillingTrialService billing;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private PlanTransferRebuildStore rebuilds;
    @org.springframework.beans.factory.annotation.Autowired
    public void rebuildStore(PlanTransferRebuildStore value) { rebuilds=value; }
    public PlanTransferService(AdPlanRepository plans,PlanTransferStore store,CreationStore creations,PlanRemoteStore remote,
                               PlanTransferGateway gateway,BillingTrialService billing,ObjectMapper json,PlatformTransactionManager manager) {
        this.plans=plans;this.store=store;this.creations=creations;this.remote=remote;this.gateway=gateway;this.billing=billing;this.json=json;tx=new TransactionTemplate(manager);
    }
    public Context context(TenantId tenant,PlanId id) {
        var g=store.group(tenant,id);var current=require(tenant,id.value());
        SmartBidType mode=opposite(current.setting().bidType());String targetId=g==null?null:g.target(mode);
        var target=targetId==null?null:require(tenant,targetId);
        String reason=null;
        boolean requiresResume=false;
        if(current.qcPlanId().isEmpty() || !editable(current.status()))reason="仅已发布且投放中或暂停的计划支持转投";
        else if((g==null || g.requestKey()==null) && rebuilds!=null && (target==null || target.transferArchived())) {
            try {
                var detail=gateway.detail(current);
                var platformStatus=status(detail);
                boolean pausedFirstTransfer=target==null && current.status()==PlanStatus.PAUSED && platformStatus==PlanStatus.PAUSED;
                if(!enabled(platformStatus) && !pausedFirstTransfer)
                    throw new BizException(1403,"请先恢复当前计划投放，再切换投放方式");
                rebuildSnapshot(tenant,g==null?id.value():g.root(),current,detail);
                if(target!=null) archivedPayload(tenant,g.root(),target);
                if(pausedFirstTransfer) {
                    requiresResume=true;
                    reason="请先恢复当前计划投放，再切换投放方式";
                }
            } catch(BizException unavailable) { reason=unavailable.getMessage(); }
        }
        else if(target==null && creations.publishedPayload(tenant.value(),g==null?id.value():g.root()).isEmpty())reason="缺少已发布的商品与素材配置，暂时无法转投";
        Attempt a=g==null||g.requestKey()==null?null:store.attempt(tenant,g.requestKey()).orElse(null);
        if(g!=null&&g.requestKey()!=null)reason=g.requestKey().startsWith("settings:")?"计划修改正在处理或结果待核查，请先核查修改结果":"转投正在处理或结果待核查";
        String name = target == null ? defaultTargetName(current) : displayName(target);
        if (target != null && !target.transferArchived()) name = PlanTransferNames.editedPlatformName(name);
        return new Context(id.value(),current.qcPlanId().map(QcPlanId::value).orElse(null),mode.oceanValue(),
                target==null?null:target.setting().dailyBudget().amount(),target==null?null:target.setting().roi2Goal().map(RoiGoal::value).orElse(null),
                target!=null,target==null?null:target.qcPlanId().map(QcPlanId::value).orElse(null),reason==null,reason,a==null?null:a.key(),a==null?null:a.status(),a==null?null:result(a).message(),
                name,targetId,requiresResume);
    }
    public List<PlanTransferRebuildStore.History> history(TenantId tenant,PlanId id) {
        require(tenant,id.value());
        return rebuilds.history(tenant,id.value());
    }
    public JsonNode recommendations(TenantId tenant, PlanId id) {
        var context = context(tenant, id);
        if (!context.available()) throw new BizException(1403, context.reason());
        var source = require(tenant, id.value());
        var mode = opposite(source.setting().bidType());
        var group = store.group(tenant, id);
        var targetId = group == null ? null : group.target(mode);
        var target = targetId == null ? source : require(tenant, targetId);
        var plan = target.transferArchived()?source:target;
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
            var sourceQc=source.qcPlanId().orElseThrow(()->new BizException(1403,"关联计划暂无正在使用的投放，请先核查转投结果"));
            return transferLocked(tenant,source.id(),new PlanTransferRequest(UUID.randomUUID().toString(),sourceQc.value(),null,null,null,true));
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
        if(target!=null && (!source.accountId().equals(target.accountId()) || !source.awemeId().equals(target.awemeId()) || target.qcPlanId().isEmpty() && !target.transferArchived()))
            throw new BizException(1404,"转投关联的账户信息不一致，请先核查计划");
        var credential=gateway.credential(source);
        var sourceDetail=gateway.detail(source);checkMode(sourceDetail,source.setting().bidType());
        transferSchedule(source,request);
        var sourceStatus=status(sourceDetail);if(!(editable(sourceStatus) || request.existingOnly() && enabled(sourceStatus)))throw new BizException(1404,"平台计划状态已变化，请先同步计划");
        if(rebuilds!=null && (target==null || target.transferArchived()))
            return beginRebuild(tenant,root,source,sourceDetail,target,request,mode);
        BigDecimal updateBudget=null,updateRoi=null;
        JsonNode targetDetail=null;
        if(target!=null) {
            var d=gateway.detail(target);checkMode(d,mode);
            if(status(d)!=PlanStatus.PAUSED && !(request.existingOnly() && enabled(status(d))))
                throw new BizException(1404,"待启用的计划状态已变化，请刷新后重试");
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
        String existingName = target == null ? null
                : QianchuanPlanMapping.info(targetDetail).path("name").asText(target.name().text());
        String targetName = null;
        if (!request.existingOnly()) {
            targetName = request.name() != null ? PlanTransferNames.editedPlatformName(new PlanName(request.name()).text())
                    : target == null ? defaultTargetName(source) : PlanTransferNames.editedPlatformName(existingName);
        }
        boolean renameTarget = targetName != null && !targetName.equals(existingName);
        if(renameTarget && plans.existsUnfinishedName(tenant,source.awemeId(),PlanName.fromPlatform(targetName),Optional.ofNullable(targetId).map(PlanId::new)))
            throw new BizException(1401,"同一抖音号存在同名计划，请修改转投计划名称");
        var payload=target==null?payload(tenant,root,source,request,sourceDetail):json.createObjectNode();
        if (targetName != null) payload.put("name",targetName);
        if (target == null) {
            var ids = productIds(payload);
            var previous = store.lastRejectedCreation(tenant, root, source.id()).orElse(null);
            if (previous != null && requiresPlatformTransfer(previous.error())
                    && request.smartBidType().equals(decode(previous.request()).smartBidType())
                    && ids.equals(productIds(parse(previous.payload())))) {
                return new Result(source.id().value(), null, "FAILED", PRODUCT_TRANSFER_BLOCKED, true);
            }
            if (mode == SmartBidType.CONSERVATIVE) {
                var recommendation = gateway.recommendations(source, List.copyOf(ids), mode);
                CreationRecommendationPolicy.applyBudgetEstimate(payload.withObject("/delivery_setting"), recommendation, request.budget());
            }
        }
        if(targetId==null)targetId=UUID.randomUUID().toString();
        var g=store.ensure(source);
        Attempt a=new Attempt(tenant.value(),request.requestKey(),g.root(),source.id().value(),targetId,encode(request),payload.toString(),enabled(sourceStatus),
                target==null?null:target.qcPlanId().orElseThrow().value(),"RUNNING","PREPARED",null,Instant.now());
        store.claim(a,g.active());
        try {
            if(target!=null) {
                if(updateBudget!=null || updateRoi!=null || renameTarget || request.scheduleType()!=null) {
                    store.phase(tenant,a.key(),"UPDATING",target.qcPlanId().orElseThrow().value());
                    if(updateBudget!=null || updateRoi!=null)gateway.settings(credential,target.qcPlanId().orElseThrow().value(),updateBudget,updateRoi);
                    if(renameTarget || request.scheduleType()!=null)
                        gateway.details(credential,target.qcPlanId().orElseThrow().value(),renameTarget?targetName:null,request.scheduleType(),request.endDate());
                    targetDetail=gateway.detail(target);
                    if(!matches(targetDetail,request,targetName))throw new BizException(1523,"目标计划设置尚未确认，请核查后继续转投");
                }
                if(request.existingOnly())applyDetail(target,targetDetail);
                else {target.updateSetting(setting(target,request));if(renameTarget)target.rename(PlanName.fromPlatform(targetName));}
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
            if(!request.existingOnly() && !matches(gateway.detail(target),request,targetName))throw new BizException(1523,"计划修改结果尚未确认，请核查后继续");
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
            require(tenant,id.value());
            var g=store.group(tenant,id);if(g==null||g.requestKey()==null)return new Result(g==null?id.value():g.active(),require(tenant,g==null?id.value():g.active()).qcPlanId().map(QcPlanId::value).orElse(null),"IDLE","当前没有待核查的转投");
            var a=store.attempt(tenant,g.requestKey()).orElseThrow();
            if("RUNNING".equals(a.status()) && a.updatedAt().isAfter(Instant.now().minusSeconds(180)))return result(a);
            var rebuild=rebuilds==null?Optional.<PlanTransferRebuildStore.Rebuild>empty():rebuilds.find(tenant,a.key());
            if(rebuild.isPresent()) {
                if(!store.startReconciliation(a))return result(store.attempt(tenant,a.key()).orElseThrow());
                return advanceRebuild(tenant,a,rebuild.get(),require(tenant,a.source()),true);
            }
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

    private Result beginRebuild(TenantId tenant,String root,AdPlan source,JsonNode detail,AdPlan target,
                                PlanTransferRequest request,SmartBidType mode) {
        if(!enabled(status(detail)))throw new BizException(1403,"请先恢复当前计划投放，再切换投放方式");
        var original=rebuildSnapshot(tenant,root,source,detail);
        var targetPayload=original.deepCopy();
        String sourceName=original.path("name").asText();
        String displayName=target==null?PlanTransferNames.defaultName(sourceName):displayName(target);
        if(request.name()!=null)displayName=PlanTransferNames.editedName(new PlanName(request.name()).text());
        String targetId=target==null?UUID.randomUUID().toString():target.id().value();
        if(plans.existsUnfinishedName(tenant,source.awemeId(),PlanName.fromPlatform(displayName),Optional.of(new PlanId(targetId))))
            throw new BizException(1401,"同一抖音号存在同名计划，请修改转投计划名称");
        var archived=target==null?null:archivedPayload(tenant,root,target);
        if(archived!=null) {
            if(!archived.path("advertiser_id").asText().equals(original.path("advertiser_id").asText())
                    || !archived.path("aweme_id").asText().equals(original.path("aweme_id").asText()))
                throw new BizException(1404,"待恢复计划的账户绑定与当前计划不一致");
            targetPayload.set("delivery_setting",archived.path("delivery_setting").deepCopy());
        }
        var ds=targetPayload.withObject("/delivery_setting");
        var desired=request.existingOnly()?target.setting():setting(target==null?source:target,request);
        ds.put("smart_bid_type",mode.oceanValue()).put("budget",desired.dailyBudget().amount());
        ds.remove(List.of("roi2_goal","estimate_roi_goal","min_estimate_roi_goal","max_estimate_roi_goal","min_estimate_convert","estimate_convert"));
        desired.roi2Goal().ifPresent(roi->ds.put("roi2_goal",roi.value()));
        writeSchedule(ds,desired.schedule().type().name(),desired.schedule().endDate());
        if(mode==SmartBidType.CONSERVATIVE) {
            var recommendation=gateway.recommendations(source,List.copyOf(productIds(targetPayload)),mode);
            CreationRecommendationPolicy.applyBudgetEstimate(ds,recommendation,desired.dailyBudget().amount());
            if(!completeBudgetEstimate(ds)) {
                ds.remove(BUDGET_ESTIMATES);
                if(archived!=null)copyVerifiedBudgetEstimate(archived,targetPayload);
                if(request.existingOnly() && !completeBudgetEstimate(ds) && recommendation!=null
                        && equal(desired.dailyBudget().amount(),QianchuanPlanMapping.number(recommendation.path("budget"),"budget")))
                    throw new BizException(1403,"暂未取得该放量预算的完整预估，原计划保持投放，请稍后再恢复");
            }
        }
        validateRebuildPayload(targetPayload);
        String sourceQc=source.qcPlanId().orElseThrow().value();
        String targetName=instanceName(displayName,tenant,request.requestKey(),"target");
        String restoreName=instanceName(sourceName,tenant,request.requestKey(),"restore");
        targetPayload.put("name",targetName);
        var saved=new PlanTransferRebuildStore.Rebuild(source.accountId().value(),sourceQc,original,detail,displayName,targetName,restoreName,null);
        var group=store.ensure(source);
        if(!group.active().equals(source.id().value()))throw new BizException(1404,"当前生效的计划已变化，请刷新后转投");
        var a=new Attempt(tenant.value(),request.requestKey(),group.root(),source.id().value(),targetId,encode(request),
                targetPayload.toString(),true,null,"RUNNING","REBUILD_PREPARED",null,Instant.now());
        tx.executeWithoutResult(s->{
            store.claim(a,group.active());
            var current=plans.findByIdForUpdate(tenant,source.id()).orElseThrow();
            if(!current.qcPlanId().map(QcPlanId::value).filter(sourceQc::equals).isPresent())
                throw new BizException(1404,"当前计划已变化，请刷新后转投");
            if(target!=null) {
                var latest=plans.findByIdForUpdate(tenant,target.id()).orElseThrow();
                if(!PlanEditVersion.snapshot(target).equals(PlanEditVersion.snapshot(latest)))
                    throw new BizException(1404,"待恢复计划设置已变化，请刷新后操作");
            }
            rebuilds.save(a,saved);
            store.phase(tenant,a.key(),a.phase(),null);
        });
        return advanceRebuild(tenant,a,saved,source,false);
    }

    private ObjectNode rebuildSnapshot(TenantId tenant,String root,AdPlan source,JsonNode detail) {
        if(source.source()!=PlanSource.LINGTOU)throw new BizException(1403,"该计划缺少可验证的原始创建配置");
        var ds=QianchuanPlanMapping.setting(detail);
        var mode=source.setting().bidType();
        checkMode(detail,mode);
        if(!PlanSchedule.complete(ds))throw new BizException(1403,"平台未返回完整投放排期，当前计划暂无法转投");
        var budget=QianchuanPlanMapping.number(ds,"budget");
        var roi=mode==SmartBidType.CUSTOM?QianchuanPlanMapping.number(ds,"roi2_goal"):null;
        var schedule=PlanSchedule.read(ds,source.setting().schedule());
        var request=new PlanTransferRequest("snapshot",source.qcPlanId().orElseThrow().value(),mode.oceanValue(),budget,roi,
                false,null,schedule.type().name(),schedule.endDate());
        DeliveryInputPolicy.settings(mode,budget,roi,DeliveryInputPolicy.RULES.creation());
        String qcId=source.qcPlanId().orElseThrow().value();
        var base=rebuilds.submittedPayload(tenant,source.id().value(),qcId)
                .or(()->creations.publishedInstancePayload(tenant.value(),root,qcId));
        var saved=payload(base,source,request,detail);
        saved.set("multi_product_creative_list",rebuildCreatives(detail,base.orElseThrow()));
        if(!"VIDEO_PROM_GOODS".equals(saved.path("marketing_goal").asText()))
            throw new BizException(1403,"当前投放类型暂无法完整重建");
        for(String field:List.of("deep_external_action","qcpx_mode","budget_mode")) {
            if(ds.hasNonNull(field))saved.withObject("/delivery_setting").set(field,ds.get(field).deepCopy());
        }
        copyVerifiedBudgetEstimate(base.orElseThrow(),saved);
        saved.put("name",displayName(source));
        validateRebuildPayload(saved);
        return saved;
    }

    /** Some plan details omit the promotion card submitted for that exact instance. */
    private static JsonNode rebuildCreatives(JsonNode detail,JsonNode submitted) {
        var programmatic=detail.path("programmatic_creative_media_list");
        boolean hasAutomaticMaterials=false;
        if(programmatic.isObject()) {
            for(var materials:programmatic)hasAutomaticMaterials|=!materials.isArray() || !materials.isEmpty();
        } else hasAutomaticMaterials=!programmatic.isMissingNode() && !programmatic.isNull()
                && (!programmatic.isArray() || !programmatic.isEmpty());
        if(hasAutomaticMaterials)
            throw new BizException(1403,"计划包含暂无法完整重建的自动素材");
        for(var group:detail.path("multi_product_creative_list")) {
            if(!group.path("block_material").isEmpty() || !group.path("block_video_material").isEmpty())
                throw new BizException(1403,"计划包含暂无法完整重建的屏蔽素材配置");
        }
        var current=QianchuanCreativeBindings.groups(detail);
        var approved=QianchuanCreativeBindings.groups(submitted);
        var byProduct=new HashMap<String,JsonNode>();
        approved.forEach(group->byProduct.put(group.path("product_id").asText(),group));
        var sameBindings=PublishedMaterialService.same(creativeProof(current),creativeProof(approved));
        for(var node:current) {
            var group=(ObjectNode)node;
            if("PROGRAMMATIC_CREATIVE".equals(group.path("creative_type").asText()))group.remove("creative_type");
            var card=group.path("creative_card");
            if(!card.isMissingNode() && !card.isNull() && !(card.isObject() && card.isEmpty()))continue;
            var original=byProduct.get(group.path("product_id").asText());
            if(sameBindings && original!=null && original.path("creative_card").hasNonNull("promotion_card_image_id"))
                group.set("creative_card",original.path("creative_card").deepCopy());
        }
        return current;
    }

    private static JsonNode creativeProof(JsonNode bindings) {
        JsonNode proof=bindings.deepCopy();
        for(var node:proof) {
            var group=(ObjectNode)node;
            group.remove(List.of("creative_card","creative_type"));
            for(var title:group.path("title_material")) {
                if(title.path("dynamic_words").isTextual() && title.path("dynamic_words").asText().isBlank())
                    ((ObjectNode)title).remove("dynamic_words");
            }
        }
        return proof;
    }

    private ObjectNode archivedPayload(TenantId tenant,String root,AdPlan target) {
        var instance=rebuilds.savedInstance(tenant,target.id().value()).orElseThrow(()->new BizException(1403,"缺少恢复所需的计划快照"));
        var saved=instance.createPayload();
        if(!saved.isObject() || !saved.path("delivery_setting").isObject())
            throw new BizException(1403,"缺少恢复所需的投放设置");
        var payload=(ObjectNode)saved.deepCopy();
        if(target.setting().bidType()==SmartBidType.CONSERVATIVE && !completeBudgetEstimate(payload.path("delivery_setting")))
            rebuilds.submittedPayload(tenant,target.id().value(),instance.qcPlanId())
                .or(()->creations.publishedInstancePayload(tenant.value(),root,instance.qcPlanId()))
                .ifPresent(accepted->copyVerifiedBudgetEstimate(accepted,payload));
        return payload;
    }

    private static boolean completeBudgetEstimate(JsonNode setting) {
        return BUDGET_ESTIMATES.stream().allMatch(field->setting.path(field).isNumber() && setting.path(field).decimalValue().signum()>=0);
    }

    private static void copyVerifiedBudgetEstimate(JsonNode accepted,ObjectNode payload) {
        var old=accepted.path("delivery_setting");
        var current=payload.withObject("/delivery_setting");
        if(!SmartBidType.CONSERVATIVE.oceanValue().equals(current.path("smart_bid_type").asText())
                || !current.path("smart_bid_type").asText().equals(old.path("smart_bid_type").asText())
                || !equal(QianchuanPlanMapping.number(old,"budget"),QianchuanPlanMapping.number(current,"budget"))
                || !accepted.path("advertiser_id").asText().equals(payload.path("advertiser_id").asText())
                || !accepted.path("aweme_id").asText().equals(payload.path("aweme_id").asText())
                || !productIds(accepted).equals(productIds(payload)) || !completeBudgetEstimate(old))return;
        BUDGET_ESTIMATES.forEach(field->current.set(field,old.get(field)));
    }

    private static void validateRebuildPayload(JsonNode payload) {
        var ds=payload.path("delivery_setting");
        var mode=DeliveryInputPolicy.mode(ds.path("smart_bid_type").asText());
        DeliveryInputPolicy.settings(mode,QianchuanPlanMapping.number(ds,"budget"),
                QianchuanPlanMapping.number(ds,"roi2_goal"),DeliveryInputPolicy.RULES.creation());
        if(!PlanSchedule.complete(ds))throw new BizException(1403,"投放排期无法重建");
        if(PlanSchedule.type(ds)==ScheduleType.DATE_RANGE &&
                QianchuanPlanMapping.date(QianchuanPlanMapping.text(ds,"end_time")).isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))))
            throw new BizException(1403,"计划投放排期已结束");
        for(var group:QianchuanCreativeBindings.groups(payload)) {
            boolean hasMedia=!group.path("video_material").isEmpty() || !group.path("image_material").isEmpty() || !group.path("carousel_material").isEmpty();
            if(!hasMedia || group.path("title_material").isEmpty() || !group.path("creative_card").hasNonNull("promotion_card_image_id"))
                throw new BizException(1403,"商品素材、标题或推广卡快照不完整，当前计划暂无法转投");
            for(var video:group.path("video_material")) if(video.path("video_cover_id").asText().isBlank() || video.path("image_mode").asText().isBlank())
                throw new BizException(1403,"视频封面或规格不完整，当前计划暂无法转投");
            for(var image:group.path("image_material")) if(image.path("image_ids").isEmpty() || image.path("image_mode").asText().isBlank())
                throw new BizException(1403,"图片素材不完整，当前计划暂无法转投");
        }
    }

    private static void writeSchedule(ObjectNode ds,String type,LocalDate end) {
        ds.put("video_schedule_type","FROM_NOW".equals(type)?"SCHEDULE_FROM_NOW":"SCHEDULE_START_END");
        ds.remove(List.of("schedule_type","start_time","end_time"));
        if(end!=null) {
            ds.put("start_time",LocalDate.now(ZoneId.of("Asia/Shanghai")).toString());
            ds.put("end_time",end.toString());
        }
    }

    private static String instanceName(String display,TenantId tenant,String key,String purpose) {
        String hash=UUID.nameUUIDFromBytes((tenant.value()+"/"+key+"/"+purpose).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-","").substring(0,12);
        String tail=display.endsWith(PlanTransferNames.SUFFIX)?PlanTransferNames.SUFFIX:"";
        if(!tail.isEmpty())display=display.substring(0,display.length()-tail.length());
        int end=Math.min(display.length(),DeliveryInputPolicy.RULES.nameMaxLength()-13-tail.length());
        if(end<display.length() && Character.isHighSurrogate(display.charAt(end-1)))end--;
        return display.substring(0,end)+tail+"_"+hash;
    }

    private Result advanceRebuild(TenantId tenant,Attempt a,PlanTransferRebuildStore.Rebuild saved,AdPlan source,boolean checking) {
        String phase=a.phase();
        try {
            var credential=gateway.credential(source);
            if(!saved.account().equals(source.accountId().value())
                    || !credential.advertiserId().value().equals(saved.sourcePayload().path("advertiser_id").asText()))
                throw new BizException(1404,"投放账户绑定已变化，请核查转投结果");
            if("REBUILD_ABORTING".equals(phase) || checking && "REBUILD_PREPARED".equals(phase))
                return failBeforeDelete(tenant,a,phase,"转投尚未提交，原计划保持原状态");
            if("REBUILD_PREPARED".equals(phase)) {
                preserveUpstreamReport(tenant,a,saved,source,credential);
                var current=gateway.detail(source,saved.sourceQcId());
                if(!enabled(status(current)) || !rebuildSnapshot(tenant,a.root(),source,current).equals(saved.sourcePayload()))
                    throw new BizException(1404,"原计划配置已变化，请刷新后转投");
                if(!store.dispatchPhase(tenant,a.key(),phase,"REBUILD_DELETING"))
                    return result(store.attempt(tenant,a.key()).orElseThrow());
                phase="REBUILD_DELETING";
                gateway.delete(credential,saved.sourceQcId());
                store.phase(tenant,a.key(),"REBUILD_DELETE_ACK",null);
                phase="REBUILD_DELETE_ACK";
            }
            if("REBUILD_DELETING".equals(phase)) {
                var current=gateway.detail(source,saved.sourceQcId());
                if(status(current)!=PlanStatus.DELETED && !"DELETE".equals(QianchuanPlanMapping.info(current).path("opt_status").asText()))
                    return pending(tenant,a,"原计划删除结果待确认，请稍后查询");
                store.phase(tenant,a.key(),"REBUILD_DELETE_ACK",null);
                phase="REBUILD_DELETE_ACK";
            }
            if("REBUILD_DELETE_ACK".equals(phase)) {
                tx.executeWithoutResult(s->{
                    rebuilds.archive(a,saved,remote.list(tenant).get(a.source()));
                    applyDetail(source,saved.sourceDetail());
                    source.rename(PlanName.fromPlatform(saved.sourcePayload().path("name").asText()));
                    source.archiveForTransfer();
                    plans.saveTransferInstance(source,saved.sourceQcId());
                    store.phase(tenant,a.key(),"REBUILD_DELETED",null);
                });
                phase="REBUILD_DELETED";
            }
            if("REBUILD_DELETED".equals(phase)) {
                var target=(ObjectNode)parse(a.payload());
                refreshRebuildSchedule(target);
                if(!store.dispatchPhase(tenant,a.key(),phase,"REBUILD_CREATING"))
                    return result(store.attempt(tenant,a.key()).orElseThrow());
                phase="REBUILD_CREATING";
                var qc=gateway.create(source,target);
                store.phase(tenant,a.key(),"REBUILD_CREATED",qc);
                phase="REBUILD_CREATED";
            }
            if("REBUILD_CREATING".equals(phase)) {
                var target=parse(a.payload());
                var matches=gateway.findCreated(credential,DeliveryInputPolicy.mode(target.path("delivery_setting").path("smart_bid_type").asText()),saved.targetPlatformName());
                if(matches.size()!=1)return pending(tenant,a,"目标计划创建结果待确认，请稍后查询");
                store.phase(tenant,a.key(),"REBUILD_CREATED",matches.getFirst().qcPlanId());
                phase="REBUILD_CREATED";
            }
            if("REBUILD_CREATED".equals(phase)) {
                var current=store.attempt(tenant,a.key()).orElseThrow();
                return confirmRebuild(tenant,current,saved,source,current.qcId(),false);
            }
            if("REBUILD_RESTORE_READY".equals(phase))return restoreRebuild(tenant,a,saved,source);
            if("REBUILD_RESTORING".equals(phase)) {
                var matches=gateway.findCreated(credential,source.setting().bidType(),saved.restorePlatformName());
                if(matches.size()!=1)return pending(tenant,a,"原投放恢复结果待确认，请稍后查询");
                String restoredQc=matches.getFirst().qcPlanId();
                tx.executeWithoutResult(s->{
                    rebuilds.restoreReceipt(tenant,a.key(),restoredQc);
                    store.phase(tenant,a.key(),"REBUILD_RESTORED",null);
                });
                return confirmRebuild(tenant,a,saved,source,restoredQc,true);
            }
            if("REBUILD_RESTORED".equals(phase))return confirmRebuild(tenant,a,saved,source,saved.restoreQcId(),true);
        } catch(RuntimeException failure) {
            if("REBUILD_PREPARED".equals(phase) || !checking && "REBUILD_DELETING".equals(phase) && rejected(failure))
                return failBeforeDelete(tenant,a,phase,"转投未提交，原计划保持原状态"+(failure instanceof BizException?"。"+failure.getMessage():""));
            if("REBUILD_DELETED".equals(phase) && failure instanceof BizException || !checking && "REBUILD_CREATING".equals(phase) && rejected(failure)) {
                LOG.warn("transfer rebuild rejected key={} target={} phase={} reason={}",a.key(),a.target(),phase,failure.getMessage());
                tx.executeWithoutResult(s->{
                    store.phase(tenant,a.key(),"REBUILD_RESTORE_READY",null);
                    store.finish(tenant,a.key(),"UNCERTAIN","目标计划未创建，正在恢复原投放");
                });
                if(store.startReconciliation(store.attempt(tenant,a.key()).orElseThrow()))
                    return restoreRebuild(tenant,a,saved,source);
                return result(store.attempt(tenant,a.key()).orElseThrow());
            }
        }
        return pending(tenant,a,"转投结果待确认，请稍后查询");
    }

    private Result failBeforeDelete(TenantId tenant,Attempt attempt,String phase,String message) {
        if("REBUILD_ABORTING".equals(phase) || store.dispatchPhase(tenant,attempt.key(),phase,"REBUILD_ABORTING"))
            store.finish(tenant,attempt.key(),"FAILED",message);
        return result(store.attempt(tenant,attempt.key()).orElseThrow());
    }

    private void preserveUpstreamReport(TenantId tenant,Attempt attempt,PlanTransferRebuildStore.Rebuild saved,
                                        AdPlan source,com.lynx.ad.modules.identity.domain.model.Credential credential) {
        var zone=ZoneId.of("Asia/Shanghai");
        var today=LocalDate.now(zone);
        var created=QianchuanPlanMapping.instant(QianchuanPlanMapping.text(QianchuanPlanMapping.info(saved.sourceDetail()),"create_time"));
        var first=created==null?source.setting().schedule().startDate():created.atZone(zone).toLocalDate();
        var earliest=today.minusDays(179);
        var start=first==null || first.isBefore(earliest)?earliest:first.isAfter(today)?today:first;
        com.lynx.ad.modules.delivery.domain.port.AdManagementPort.InsightReport report;
        try { report=gateway.history(credential,saved.sourceQcId(),start,today); }
        catch(BizException unavailable) { throw new BizException(1406,"暂未取得原计划投放报表，请稍后再试"); }
        if(report==null || !report.summary().isEmpty() && !saved.sourceQcId().equals(PlanInsightsService.dimension(report.summary(),"ad_id"))
                || report.trend().stream().anyMatch(row->!saved.sourceQcId().equals(PlanInsightsService.dimension(row,"ad_id"))))
            throw new BizException(1406,"未能核对原计划投放数据，请稍后再试");
        rebuilds.saveReport(tenant,attempt.key(),start,today,report);
    }

    private Result restoreRebuild(TenantId tenant,Attempt a,PlanTransferRebuildStore.Rebuild saved,AdPlan source) {
        var restore=(ObjectNode)saved.sourcePayload().deepCopy();
        restore.put("name",saved.restorePlatformName());
        String qc;
        try {
            refreshRebuildSchedule(restore);
            if(!store.dispatchPhase(tenant,a.key(),"REBUILD_RESTORE_READY","REBUILD_RESTORING"))
                return result(store.attempt(tenant,a.key()).orElseThrow());
            qc=gateway.create(source,restore);
        } catch(RuntimeException failure) {
            // A business rejection proves there is no duplicate to create on a later, deliberate retry.
            if(rejected(failure))store.phase(tenant,a.key(),"REBUILD_RESTORE_READY",null);
            return pending(tenant,a,"目标计划未创建，原投放尚未恢复，请核查转投结果");
        }
        tx.executeWithoutResult(s->{
            rebuilds.restoreReceipt(tenant,a.key(),qc);
            store.phase(tenant,a.key(),"REBUILD_RESTORED",null);
        });
        try { return confirmRebuild(tenant,a,saved,source,qc,true); }
        catch(RuntimeException failure) { return pending(tenant,a,"原投放已提交恢复，最新状态待确认"); }
    }

    private Result confirmRebuild(TenantId tenant,Attempt a,PlanTransferRebuildStore.Rebuild saved,AdPlan source,String qc,boolean restoring) {
        var payload=restoring?saved.sourcePayload():parse(a.payload());
        var ds=payload.path("delivery_setting");
        var mode=DeliveryInputPolicy.mode(ds.path("smart_bid_type").asText());
        var schedule=PlanSchedule.read(ds,Schedule.fromNow());
        var request=new PlanTransferRequest(a.key(),saved.sourceQcId(),mode.oceanValue(),QianchuanPlanMapping.number(ds,"budget"),
                QianchuanPlanMapping.number(ds,"roi2_goal"),false,null,schedule.type().name(),schedule.endDate());
        var detail=gateway.detail(source,qc);
        String issuedName=restoring?saved.restorePlatformName():saved.targetPlatformName();
        if(!matches(detail,request,issuedName) || !qc.equals(QianchuanPlanMapping.id(detail))
                || !PublishedMaterialService.same(rebuildCreatives(detail,payload),rebuildCreatives(payload,payload)))
            return pending(tenant,a,"计划已创建，投放配置待核查");
        var status=status(detail);
        if(status==PlanStatus.UNKNOWN || status==PlanStatus.DELETED)
            return pending(tenant,a,"计划已创建，投放状态待核查");
        String logicalId=restoring?a.source():a.target();
        var known=plans.findById(tenant,new PlanId(logicalId));
        var displayName=restoring?saved.sourcePayload().path("name").asText():saved.targetName();
        var target=known.orElseGet(()->new AdPlan(new PlanId(logicalId),tenant,source.accountId(),source.awemeId(),
                PlanName.fromPlatform(displayName),status,PlanSource.LINGTOU,setting(source,request),
                productIds(payload).stream().map(QcProductId::new).toList(),new QcPlanId(qc),status.name(),null,1,null,source.bindings()));
        target.rebuildForTransfer(new QcPlanId(qc));
        applyDetail(target,detail);
        target.rename(PlanName.fromPlatform(displayName));
        String message;
        if(restoring)message=switch(status) {
            case DELIVERING -> "目标计划创建失败，原投放已恢复";
            case PENDING_AUDIT -> "目标计划创建失败，原计划已重建，正在审核";
            default -> "目标计划创建失败，原计划已重建，请查看最新投放状态";
        };
        else message=switch(status) {
            case DELIVERING -> null;
            case PENDING_AUDIT -> "原计划已暂停，目标计划已创建，正在审核";
            default -> "原计划已暂停，目标计划已创建，请查看最新投放状态";
        };
        tx.executeWithoutResult(s->{
            plans.saveTransferInstance(target,null);
            remote.replaceInstance(tenant,target.accountId(),qc,logicalId,payload);
            remote.detail(tenant,logicalId,detail);
            store.member(tenant,a.root(),mode,logicalId);
            store.finish(tenant,a.key(),restoring?"FAILED":"SUCCEEDED",message);
        });
        return result(store.attempt(tenant,a.key()).orElseThrow());
    }

    private static void refreshRebuildSchedule(ObjectNode payload) {
        validateRebuildPayload(payload);
        var ds=payload.withObject("/delivery_setting");
        if(PlanSchedule.type(ds)==ScheduleType.DATE_RANGE)
            writeSchedule(ds,"DATE_RANGE",QianchuanPlanMapping.date(QianchuanPlanMapping.text(ds,"end_time")));
    }

    private static boolean rejected(RuntimeException failure) {
        return failure instanceof BizException e && e.code()==1406;
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
    private ObjectNode payload(TenantId tenant,String root,AdPlan source,PlanTransferRequest request,JsonNode sourceDetail) {
        return payload(creations.publishedPayload(tenant.value(),root),source,request,sourceDetail);
    }
    private ObjectNode payload(Optional<JsonNode> base,AdPlan source,PlanTransferRequest request,JsonNode sourceDetail) {
        var p=base.orElseThrow(()->new BizException(1403,"缺少已发布的商品与素材配置，暂时无法转投"));
        if(!p.isObject() || !p.path("multi_product_creative_list").isArray() || p.path("multi_product_creative_list").isEmpty() || !p.path("aweme_id").asText().equals(source.awemeId().value()))
            throw new BizException(1403,"已发布素材配置不完整，无法转投");
        var result=(ObjectNode)p.deepCopy();result.remove("ad_id");
        // Publish attempts and shared creation state are historical records. Reuse
        // the current physical source's complete bindings, read before any write.
        // This includes later additions/removals, covers, titles and visibility.
        if(!source.qcPlanId().orElseThrow().value().equals(QianchuanPlanMapping.id(sourceDetail)))
            throw new BizException(1404,"平台返回的计划不匹配");
        var liveBindings=QianchuanCreativeBindings.groups(sourceDetail);
        String aweme=QianchuanPlanMapping.text(sourceDetail,"aweme_id");
        if(aweme!=null&&!source.awemeId().value().equals(aweme))throw new BizException(1404,"计划的抖音号绑定已变化");
        result.set("multi_product_creative_list",liveBindings);
        var productIds=result.putArray("product_ids");
        var allowed=new HashSet<String>();
        for(var item:liveBindings){String product=item.path("product_id").asText();allowed.add(product);productIds.add(new java.math.BigInteger(product));}
        // Never carry channels for products removed from the source plan.
        var channels=sourceDetail.has("product_channel_info")?sourceDetail.path("product_channel_info"):p.path("product_channel_info");
        result.remove("product_channel_info");
        if(channels.isArray()) {var current=result.putArray("product_channel_info");for(var channel:channels)if(allowed.contains(channel.path("product_id").asText()))current.add(channel.deepCopy());}
        ObjectNode ds=result.withObject("/delivery_setting");ds.put("smart_bid_type",request.smartBidType());ds.put("budget",request.budget());
        ds.remove(List.of("roi2_goal","estimate_roi_goal","min_estimate_roi_goal","max_estimate_roi_goal","min_estimate_convert","estimate_convert"));
        if(request.smartBidType().endsWith("CUSTOM"))ds.put("roi2_goal",request.roi2Goal());
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var schedule=transferSchedule(source,request);
        if(schedule.endDate()!=null && schedule.endDate().isBefore(today))throw new BizException(1403,"计划已到期，无法转投");
        ds.put("video_schedule_type",schedule.type()==ScheduleType.FROM_NOW?"SCHEDULE_FROM_NOW":"SCHEDULE_START_END");
        ds.remove(List.of("schedule_type","start_time","end_time"));
        if(schedule.type()==ScheduleType.DATE_RANGE){ds.put("start_time",today.toString());ds.put("end_time",schedule.endDate().toString());}
        return result;
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
    private String displayName(AdPlan plan) {
        return rebuilds==null?plan.name().text():rebuilds.displayName(plan);
    }
    private String defaultTargetName(AdPlan source) {
        String name=displayName(source);
        return rebuilds==null?PlanTransferNames.defaultPlatformName(name):PlanTransferNames.defaultName(name);
    }
    private void paused(AdPlan p){p.apply(PlanStatus.PAUSED);p.syncPlatformStatus("DISABLE");plans.save(p);}
    private void refresh(AdPlan p){var d=gateway.detail(p);applyDetail(p,d);plans.save(p);remote.detail(p.tenantId(),p.id().value(),d);}
    private void applyDetail(AdPlan p,JsonNode d){
        p.apply(status(d));p.syncPlatformStatus(p.status()==PlanStatus.PAUSED?"DISABLE":QianchuanPlanMapping.text(QianchuanPlanMapping.info(d),"status"));
        if(QianchuanPlanMapping.info(d).hasNonNull("name")) {
            String name=QianchuanPlanMapping.info(d).path("name").asText();
            String qcId=p.qcPlanId().map(QcPlanId::value).orElse(null);
            p.rename(PlanName.fromPlatform(rebuilds==null || qcId==null?name:rebuilds.logicalName(p.tenantId(),p.id().value(),qcId,name)));
        }
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
        boolean reconstructing = a.phase().startsWith("REBUILD_");
        boolean platformTransfer = !reconstructing && productConflict && "FAILED".equals(a.status());
        String message;
        if (succeeded) message = a.error()==null?"转投成功，原计划已暂停":a.error();
        else if (platformTransfer) message = PRODUCT_TRANSFER_BLOCKED + (a.sourceEnabled() ? "原计划状态已恢复。" : "原计划保持暂停。");
        else if (!reconstructing && productConflict) message = "转投结果待核查，请稍后查询。";
        else message = a.error() == null ? "转投正在处理，请稍后核查" : a.error();
        String qcId=a.qcId();
        if("FAILED".equals(a.status()) && "REBUILD_RESTORED".equals(a.phase()) && rebuilds!=null)
            qcId=rebuilds.find(new TenantId(a.tenant()),a.key()).map(PlanTransferRebuildStore.Rebuild::restoreQcId).orElse(null);
        return new Result(succeeded ? a.target() : a.source(), qcId, a.status(), message, platformTransfer);
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
