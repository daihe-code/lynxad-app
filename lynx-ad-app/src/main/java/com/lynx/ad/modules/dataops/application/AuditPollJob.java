package com.lynx.ad.modules.dataops.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.dataops.domain.model.AuditVerdict;
import com.lynx.ad.modules.dataops.domain.service.AuditMappingPolicy;
import com.lynx.ad.modules.delivery.application.PlanCommandService;
import com.lynx.ad.modules.delivery.domain.model.AdPlan;
import com.lynx.ad.modules.delivery.domain.port.AdListPort;
import com.lynx.ad.modules.delivery.domain.port.AdPlanRepository;
import com.lynx.ad.modules.delivery.domain.port.OceanPlanRow;
import com.lynx.ad.modules.identity.application.QianchuanCredentialFacade;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditPollJob {

    private static final Logger log = LoggerFactory.getLogger(AuditPollJob.class);

    private final AdPlanRepository planRepository;
    private final PlanCommandService planCommandService;
    private final LynxAdProperties properties;
    private final AdListPort adListPort;
    private final QianchuanCredentialFacade credentialFacade;
    private final AuditMappingPolicy mappingPolicy = new AuditMappingPolicy();

    public AuditPollJob(
            AdPlanRepository planRepository,
            PlanCommandService planCommandService,
            LynxAdProperties properties,
            AdListPort adListPort,
            QianchuanCredentialFacade credentialFacade) {
        this.planRepository = planRepository;
        this.planCommandService = planCommandService;
        this.properties = properties;
        this.adListPort = adListPort;
        this.credentialFacade = credentialFacade;
    }

    @Scheduled(fixedDelayString = "${lynx.ad.schedule.audit-poll-ms:30000}")
    public void pollPendingAudits() {
        if (properties.getMock().isQianchuan()) {
            String raw = properties.getMock().getAutoAudit();
            AuditVerdict verdict = mappingPolicy.verdictOf(raw);
            for (AdPlan plan : planRepository.findPendingAudit()) {
                planCommandService.markAudit(plan.tenantId(), plan.id(), verdict, verdict == AuditVerdict.REJECT ? "审核拒绝" : null);
            }
            return;
        }
        java.util.Map<String,List<OceanPlanRow>> accountRows = new java.util.HashMap<>();
        java.util.Set<String> failedAccounts = new java.util.HashSet<>();
        record Read(AdPlan plan,com.lynx.ad.modules.delivery.infrastructure.persistence.PlanReviewStore.Claim claim) {}
        List<Read> ready=new java.util.ArrayList<>();
        // Every claim must precede the account response shared by these plans.
        for (AdPlan plan : planRepository.findPendingAudit()) {
            if (plan.qcPlanId().isEmpty()) continue;
            var claim=reviews==null?null:reviews.claim(plan,false).orElse(null);
            if(reviews!=null && claim==null)continue;
            ready.add(new Read(plan,claim));
        }
        for(var read:ready) {
            var plan=read.plan();var claim=read.claim();
            String accountKey=plan.tenantId().value()+":"+plan.accountId().value();
            try {
                if(failedAccounts.contains(accountKey)){finish(claim,"FAILED",()->{});continue;}
                List<OceanPlanRow> rows;
                try { rows=accountRows.computeIfAbsent(accountKey,key->adListPort.list(credentialFacade.getUsableCredential(plan.tenantId(),plan.accountId()),null)); }
                catch(RuntimeException failure){failedAccounts.add(accountKey);throw failure;}
                accept(plan,claim,rows);
            } catch (Exception ex) {
                finish(claim,"FAILED",()->{});
                log.warn("live audit poll failed plan={}",plan.id().value());
            }
        }
    }

    private com.lynx.ad.modules.delivery.infrastructure.persistence.PlanReviewStore reviews;
    @org.springframework.beans.factory.annotation.Autowired
    public void reviewStore(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanReviewStore value){reviews=value;}

    /** Only reads Qianchuan. The durable manual cooldown also applies across tabs and App instances. */
    public void refresh(AdPlan plan) {
        if(plan.status()!=com.lynx.ad.modules.delivery.domain.model.PlanStatus.PENDING_AUDIT || plan.qcPlanId().isEmpty())return;
        var claim=reviews.claim(plan,true).orElse(null);
        if(claim==null)return;
        try { accept(plan,claim,adListPort.list(credentialFacade.getUsableCredential(plan.tenantId(),plan.accountId()),null)); }
        catch(RuntimeException failure){finish(claim,"FAILED",()->{});log.warn("manual audit read failed plan={}",plan.id().value());}
    }
    private void accept(AdPlan plan,com.lynx.ad.modules.delivery.infrastructure.persistence.PlanReviewStore.Claim claim,List<OceanPlanRow> rows) {
        var match=rows.stream().filter(r->plan.qcPlanId().orElseThrow().value().equals(r.qcPlanId())).findFirst().orElse(null);
        if(match==null){finish(claim,"NOT_FOUND",()->{});return;}
        var status=com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping.status(match.status()==null?null:match.status().toUpperCase(java.util.Locale.ROOT));
        if(status==com.lynx.ad.modules.delivery.domain.model.PlanStatus.UNKNOWN || status==com.lynx.ad.modules.delivery.domain.model.PlanStatus.DRAFT){finish(claim,"UNKNOWN",()->{});return;}
        var verdict=status==com.lynx.ad.modules.delivery.domain.model.PlanStatus.DELIVERING?AuditVerdict.PASS:status==com.lynx.ad.modules.delivery.domain.model.PlanStatus.REJECTED?AuditVerdict.REJECT:null;
        finish(claim,status==com.lynx.ad.modules.delivery.domain.model.PlanStatus.PENDING_AUDIT?"PENDING":"RESOLVED",()->{
            if(verdict!=null)planCommandService.markAudit(plan.tenantId(),plan.id(),verdict,verdict==AuditVerdict.REJECT?"审核未通过，请前往千川查看具体原因":null);
            else if(status!=com.lynx.ad.modules.delivery.domain.model.PlanStatus.PENDING_AUDIT){plan.apply(status);plan.syncPlatformStatus(match.status());planRepository.save(plan);}
        });
    }
    private boolean finish(com.lynx.ad.modules.delivery.infrastructure.persistence.PlanReviewStore.Claim claim,String result,Runnable apply) {
        if(claim==null){apply.run();return true;}
        return reviews.finish(claim,result,apply);
    }

}
