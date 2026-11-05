package com.lynx.ad.modules.agentops.application;

import com.lynx.ad.common.domain.Ids.*;
import com.lynx.ad.modules.agentops.infrastructure.*;
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
    public record Details(OptimizationMonitoringService.View view,long effectiveSeconds,OptimizationAutomationStore.RuleSummary summary,List<OptimizationAutomationStore.Event> pending,OptimizationAutomationStore.Page history,String accountStatus,String accountName,List<OptimizationAutomationStore.Event> actions,List<OptimizationReviewStore.Review> reviews){}
    public Details details(TenantId tenant,PlanId id,boolean today,String category,int days,int page,int size){
        var view=monitor.rulesView(tenant,id); // Ownership/lifecycle check precedes every history read.
        var account=accounts.findById(tenant,new AccountId(view.accountId())).orElse(null);
        return new Details(view,metrics.seconds(tenant.value(),id.value(),today),journal.ruleSummary(tenant.value(),id.value(),today),journal.rulePending(tenant.value(),id.value()),journal.ruleHistory(tenant.value(),id.value(),category,days,page,size),account==null?"UNAVAILABLE":account.status(),account==null?null:account.accountName(),journal.ruleActions(tenant.value(),id.value(),view.version()),reviews==null?List.of():reviews.history(tenant.value(),id.value(),today));
    }
}
