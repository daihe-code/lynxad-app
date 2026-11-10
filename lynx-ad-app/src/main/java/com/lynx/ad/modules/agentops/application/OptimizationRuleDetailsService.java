package com.lynx.ad.modules.agentops.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.agentops.infrastructure.*;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OptimizationRuleDetailsService {
    private final OptimizationMonitoringService monitor;
    private final OptimizationAutomationStore journal;
    private final OptimizationRuleMetricsStore metrics;
    private final com.lynx.ad.modules.identity.domain.port.OauthAccountRepository accounts;
    private OptimizationReviewStore reviews;
    @org.springframework.beans.factory.annotation.Autowired public void reviews(OptimizationReviewStore value){reviews=value;}
    public OptimizationRuleDetailsService(OptimizationMonitoringService monitor,OptimizationAutomationStore journal,OptimizationRuleMetricsStore metrics,com.lynx.ad.modules.identity.domain.port.OauthAccountRepository accounts){this.monitor=monitor;this.journal=journal;this.metrics=metrics;this.accounts=accounts;}
    public record Details(OptimizationMonitoringService.View view,long effectiveSeconds,OptimizationAutomationStore.RuleSummary summary,List<OptimizationAutomationStore.Event> pending,OptimizationAutomationStore.Page history,String accountStatus,String accountName,List<OptimizationAutomationStore.Event> actions,List<OptimizationReviewStore.Review> reviews,OptimizationAutomationStore.PausedRules pause,Instant firstEnabledAt){}
    public Details details(TenantId tenant,PlanId id,boolean today,String category,int days,int page,int size){
        var view=monitor.rulesView(tenant,id); // Ownership/lifecycle check precedes every history read.
        var account=accounts.findById(tenant,new AccountId(view.accountId())).orElse(null);
        String tenantId=tenant.value(), planId=id.value();
        boolean enabled=view.policy().enabled();
        var pause=!enabled && view.policy().rules()!=null
            ?journal.lastRulePause(tenantId,planId,view.version()).orElse(null):null;
        var snapshot=pause==null?null:pause.snapshot();
        long seconds=snapshot==null?metrics.seconds(tenantId,planId,enabled && today):snapshot.effectiveSeconds();
        var summary=snapshot==null?journal.ruleSummary(tenantId,planId,enabled && today):snapshot.summary();
        return new Details(view,seconds,summary,journal.rulePending(tenantId,planId),
            journal.ruleHistory(tenantId,planId,category,days,page,size),account==null?"UNAVAILABLE":account.status(),
            account==null?null:account.accountName(),enabled?journal.ruleActions(tenantId,planId,view.version()):List.of(),
            reviews==null?List.of():reviews.history(tenantId,planId,enabled && today),pause,
            journal.firstRuleEnabledAt(tenantId,planId));
    }
}
