package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.agentops.infrastructure.*;
import com.lynx.ad.modules.delivery.domain.model.PlanStatus;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OptimizationCenterService {
    public record Row(String planId,String qcId,String planName,String accountId,String accountName,String planStatus,boolean enabled,String mode,long version,String holdReason,Instant lastSuccess,Instant lastAction,String error,boolean stale,JsonNode result){}
    public record Account(String accountId,String name,BigDecimal budgetLimit){}
    public record Status(String planId,boolean enabled,String mode,String holdReason,boolean stale,boolean failed,String state,boolean ruleBased){}
    public record Center(List<Row> records,long total,long watched,long automatic,long held,List<Account> accounts,int pageNum,int pageSize){}
    private final AdPlanRepository plans;private final OauthAccountRepository accounts;private final OptimizationStore store;private final OptimizationAutomationStore journal;private final OptimizationExecutionService execution;
    public OptimizationCenterService(AdPlanRepository plans,OauthAccountRepository accounts,OptimizationStore store,OptimizationAutomationStore journal,OptimizationExecutionService execution){this.plans=plans;this.accounts=accounts;this.store=store;this.journal=journal;this.execution=execution;}
    /** Local batch read only: listing plans must not trigger platform or engine requests. */
    public List<Status> statuses(TenantId tenant,List<String> ids){
        if(ids==null || ids.isEmpty() || ids.size()>50 || ids.stream().anyMatch(id->id==null || id.isBlank() || id.length()>64))throw new BizException(1802,"请选择1–50条计划");
        var now=Instant.now();
        return store.forPlans(tenant,ids.stream().distinct().toList()).stream().map(s->new Status(s.plan(),s.enabled(),s.policy().mode(),s.holdReason(),
            stale(s,now),s.error()!=null,s.result()==null?null:s.result().path("state").asText(null),s.policy().rules()!=null)).toList();
    }
    private static boolean stale(OptimizationStore.State state,Instant now){
        long freshness=state.policy().rules()==null?600:180;
        if(state.lastSuccess()==null || state.lastSuccess().plusSeconds(freshness).isBefore(now) || state.result()==null)return true;
        try {var observed=Instant.parse(state.result().path("observed_at").asText());return observed.plusSeconds(freshness).isBefore(now) || observed.isAfter(now.plusSeconds(60));}
        catch(RuntimeException ex){return true;}
    }
    public Center center(TenantId tenant,String account,String state,String query,int page,int size){
        if(page<1 || size<1 || size>100)throw new BizException(1802,"分页范围不正确");
        var names=new HashMap<String,String>();accounts.findByTenant(tenant).forEach(a->names.put(a.id().value(),a.accountName()));var states=new HashMap<String,OptimizationStore.State>();store.forTenant(tenant).forEach(s->states.put(s.plan(),s));var rows=new ArrayList<Row>();
        for(var p:plans.findByTenant(tenant)){if(p.status()==PlanStatus.DELETED || p.qcPlanId().isEmpty())continue;var s=states.get(p.id().value());boolean enabled=s!=null && s.enabled();rows.add(new Row(p.id().value(),p.qcPlanId().orElseThrow().value(),p.name().text(),p.accountId().value(),names.getOrDefault(p.accountId().value(),"广告账户"),p.status().name(),enabled,s==null?"OBSERVE":s.policy().mode(),s==null?0:s.version(),s==null?null:s.holdReason(),s==null?null:s.lastSuccess(),s==null?null:s.lastAction(),s==null?null:s.error(),s==null || s.lastSuccess()==null || s.lastSuccess().plusSeconds(600).isBefore(Instant.now()),s==null?null:s.result()));}
        long watched=rows.stream().filter(Row::enabled).count(),automatic=rows.stream().filter(r->r.enabled() && "AUTO_LIMITED".equals(r.mode()) && r.holdReason()==null).count(),held=rows.stream().filter(r->r.enabled() && r.holdReason()!=null).count();
        var filtered=rows.stream().filter(r->account==null || account.isBlank() || account.equals(r.accountId())).filter(r->query==null || query.isBlank() || r.planName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)) || r.qcId().contains(query)).filter(r->state==null || state.isBlank() || switch(state){case "OFF"->!r.enabled();case "AUTO"->r.enabled() && "AUTO_LIMITED".equals(r.mode()) && r.holdReason()==null;case "HELD"->r.enabled() && r.holdReason()!=null;case "ERROR"->r.error()!=null;case "OBSERVE"->r.enabled() && "OBSERVE".equals(r.mode());default->false;}).sorted(Comparator.comparing(Row::planId)).toList();
        var limits=new HashMap<String,BigDecimal>();journal.limits(tenant.value()).forEach(a->limits.put(a.accountId(),a.budgetLimit()));
        return new Center(filtered.stream().skip((long)(page-1)*size).limit(size).toList(),filtered.size(),watched,automatic,held,names.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Account(e.getKey(),e.getValue(),limits.get(e.getKey()))).toList(),page,size);
    }
    public void limit(TenantId tenant,String account,BigDecimal value){accounts.findById(tenant,new AccountId(account)).orElseThrow(()->new BizException(1201,"账户不存在"));journal.setLimit(tenant.value(),account,value);}
    public void holdAll(TenantId tenant){for(var p:plans.findByTenant(tenant))if(p.status()!=PlanStatus.DELETED)execution.hold(p,"用户在全店中心暂停了自动调控；继续采集和生成建议");}
}
