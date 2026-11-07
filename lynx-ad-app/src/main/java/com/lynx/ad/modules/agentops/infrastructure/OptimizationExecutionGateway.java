package com.lynx.ad.modules.agentops.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.billing.application.BillingTrialService;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.delivery.application.PlanManagementService;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.port.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import com.lynx.ad.modules.delivery.infrastructure.persistence.PlanTransferStore;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit tenant credentials and a small write whitelist; never uses an LLM-generated API request. */
@Component
public class OptimizationExecutionGateway {
    private final AdPlanRepository plans;private final PlanManagementService management;private final AdManagementPort ads;
    private final AdStatusPort statuses;private final AdListPort listing;private final QianchuanCredentialFacade credentials;
    private final PlanTransferStore transfers;private final BillingTrialService billing;private final LynxAdProperties properties;
    private final ObjectMapper json;private final TransactionTemplate tx;
    private OptimizationTacticsGateway tactics;
    @org.springframework.beans.factory.annotation.Autowired public void tactics(OptimizationTacticsGateway value){tactics=value;}
    public OptimizationTacticsGateway tactics(){return tactics;}
    public OptimizationExecutionGateway(AdPlanRepository plans,PlanManagementService management,AdManagementPort ads,AdStatusPort statuses,AdListPort listing,
            QianchuanCredentialFacade credentials,PlanTransferStore transfers,BillingTrialService billing,LynxAdProperties properties,ObjectMapper json,PlatformTransactionManager manager){
        this.plans=plans;this.management=management;this.ads=ads;this.statuses=statuses;this.listing=listing;this.credentials=credentials;this.transfers=transfers;this.billing=billing;this.properties=properties;this.json=json;tx=new TransactionTemplate(manager);
    }
    public AdPlan plan(String tenant,String id){return plans.findById(new TenantId(tenant),new PlanId(id)).filter(p->p.status()!=PlanStatus.DELETED && p.qcPlanId().isPresent()).orElseThrow(()->new BizException(1405,"已发布计划不存在"));}
    public record PlanFacts(JsonNode facts,String qcStatus) {}
    public JsonNode facts(AdPlan p){return readPlanFacts(p).facts();}
    public PlanFacts readPlanFacts(AdPlan p){
        var value=management.monitoringFacts(p.tenantId(),p.id());
        var node=json.createObjectNode().put("qcId",value.qcId()).put("status",value.status())
            .put("bidType",value.bidType()).put("budget",value.budget().stripTrailingZeros().toPlainString())
            .put("roi",value.roi()==null?null:value.roi().stripTrailingZeros().toPlainString())
            .put("deepExternalAction",value.deepExternalAction());
        var products=node.putArray("productIds");
        value.productIds().stream().sorted().forEach(products::add);
        if(tactics!=null){
            var bindings=tactics.bindingFacts(p);
            if(bindings!=null)node.set("bindings",bindings);
        }
        return new PlanFacts(node,value.qcStatus());
    }
    public boolean ready(){return !properties.getMock().isQianchuan();}
    public JsonNode reviewFacts(AdPlan p){var result=(com.fasterxml.jackson.databind.node.ObjectNode)facts(p);if(tactics!=null)result.set("bindings",tactics.merchantBindings(p));return result;}
    public void writable(AdPlan p){if(!ready())throw new BizException(1801,"自动执行仅支持正式千川数据源");billing.assertWritable(p.tenantId(),Instant.now(),WriteAction.SAVE_PLAN);}
    public List<String> related(AdPlan p){var g=transfers.group(p.tenantId(),p.id());if(g==null)return List.of(p.id().value());return java.util.stream.Stream.of(g.root(),g.conservative(),g.custom()).filter(Objects::nonNull).distinct().toList();}
    public void lock(AdPlan p,String eventId){tx.executeWithoutResult(s->transfers.lockForProductChange(p,"optimization:"+eventId));}
    public void unlock(AdPlan p,String eventId){var g=transfers.group(p.tenantId(),p.id());if(g!=null && ("optimization:"+eventId).equals(g.requestKey()))tx.executeWithoutResult(s->transfers.finishProductChange(p.tenantId(),g.root(),g.requestKey()));}
    public void assertPeerStopped(AdPlan p){for(String id:related(p)){if(id.equals(p.id().value()))continue;var peer=plans.findById(p.tenantId(),new PlanId(id)).orElse(null);if(peer==null || peer.status()==PlanStatus.DELETED || peer.qcPlanId().isEmpty())continue;String status=facts(peer).path("status").asText();if(!Set.of("PAUSED","FINISHED","DELETED").contains(status))throw new BizException(1803,"关联转投计划仍在运行或状态不明确，不自动启用当前计划");}}
    public BigDecimal accountBudget(AdPlan p){var c=credentials.getUsableCredential(p.tenantId(),p.accountId());BigDecimal sum=BigDecimal.ZERO;Set<String> seen=new HashSet<>();for(var mode:SmartBidType.values())for(var row:listing.list(c,mode)){
        if(!seen.add(row.qcPlanId()))throw new BizException(1803,"平台账户计划列表重复，暂不增加预算");
        if(QianchuanPlanMapping.status(row.status())==PlanStatus.UNKNOWN)throw new BizException(1803,"账户中存在状态不明确的计划，暂不扩量");
        if(QianchuanPlanMapping.status(row.status())==PlanStatus.DELIVERING){if(row.budget()==null || row.budget().amount().signum()<=0)throw new BizException(1803,"账户中存在日预算不明确的投放计划");sum=sum.add(row.budget().amount());}
    }return sum;}
    public BigDecimal activeTaskBudget(AdPlan p){var c=credentials.getUsableCredential(p.tenantId(),p.accountId());BigDecimal total=BigDecimal.ZERO;for(var row:listing.list(c,SmartBidType.CUSTOM))for(var task:ads.controls(c,row.qcPlanId())){
        String status=task.path("task_status").asText();if(Set.of("FROZEN","OFFLINE_TIME","OFFLINE_BUDGET","DELETED").contains(status))continue;
        if(!Set.of("PROCESSING","DISABLE","OFFLINE_BALANCE","ROI2_DISABLE","LIVE_ROOM_OFF").contains(status)||!task.hasNonNull("budget"))throw new BizException(1803,"账户调控任务预算或状态不明确，暂不追加预算");
        total=total.add(new BigDecimal(task.path("budget").asText()));
    }return total;}
    public void write(AdPlan p,JsonNode action){var c=credentials.getUsableCredential(p.tenantId(),p.accountId());String id=p.qcPlanId().orElseThrow().value();switch(action.path("kind").asText()){
        case "BUDGET"->ads.settings(c,id,null,new BigDecimal(action.path("target").asText()),null);
        case "ROI"->{var detail=ads.detail(c,id);if(!id.equals(detail.path("ad_id").asText()))throw new BizException(1803,"平台计划绑定变化");String deep=QianchuanPlanMapping.text(detail.path("delivery_setting"),"deep_external_action");if(!"AD_CONVERT_TYPE_LIVE_PURE_PAY_ROI".equals(deep))throw new BizException(1803,"自动ROI仅支持净成交ROI优化目标");ads.settings(c,id,null,null,new BigDecimal(action.path("target").asText()),deep);}
        case "PAUSE"->statuses.updateStatus(c,p.qcPlanId().orElseThrow(),PlanAction.DISABLE);
        case "ENABLE"->statuses.updateStatus(c,p.qcPlanId().orElseThrow(),PlanAction.ENABLE);
        default->throw new BizException(1802,"调控动作未列入自动执行范围");
    }}
    public void sync(AdPlan p,JsonNode facts){var old=p.setting();p.updateSetting(new DeliverySetting(SmartBidType.valueOf(facts.path("bidType").asText()),Money.ofYuan(new BigDecimal(facts.path("budget").asText())),facts.hasNonNull("roi")?Optional.of(new RoiGoal(new BigDecimal(facts.path("roi").asText()))):Optional.empty(),old.schedule(),old.audience()));p.apply(PlanStatus.valueOf(facts.path("status").asText()));p.syncPlatformStatus(facts.path("status").asText());plans.save(p);if(tactics!=null&&facts.path("actionConfirmed").asBoolean()&&facts.path("operation").path("members").size()>0){tactics.sync(p);tactics.confirmedRemoval(p,facts.path("operation"));}}
}
