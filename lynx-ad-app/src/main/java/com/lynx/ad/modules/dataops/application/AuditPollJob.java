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
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
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
    private final InboxCommandService inboxCommandService;
    private final AdListPort adListPort;
    private final QianchuanCredentialFacade credentialFacade;
    private final AuditMappingPolicy mappingPolicy = new AuditMappingPolicy();

    public AuditPollJob(
            AdPlanRepository planRepository,
            PlanCommandService planCommandService,
            LynxAdProperties properties,
            InboxCommandService inboxCommandService,
            AdListPort adListPort,
            QianchuanCredentialFacade credentialFacade) {
        this.planRepository = planRepository;
        this.planCommandService = planCommandService;
        this.properties = properties;
        this.inboxCommandService = inboxCommandService;
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
                if (verdict == AuditVerdict.REJECT) {
                    inboxCommandService.post(plan.tenantId(), "计划审核未通过", "请修改后重新发布", new BizRef("AUDIT_REJECT", plan.id().value()));
                }
            }
            return;
        }
        for (AdPlan plan : planRepository.findPendingAudit()) {
            if (plan.qcPlanId().isEmpty()) {
                continue;
            }
            try {
                List<OceanPlanRow> rows = adListPort.list(credentialFacade.get(plan.accountId()), plan.setting().bidType());
                OceanPlanRow match = rows.stream()
                        .filter(r -> plan.qcPlanId().get().value().equals(r.qcPlanId()))
                        .findFirst()
                        .orElse(null);
                if (match == null) {
                    continue;
                }
                String st = match.status() == null ? "" : match.status().toUpperCase();
                if (st.contains("REJECT") || st.contains("FAIL") || st.contains("DENY")) {
                    planCommandService.markAudit(plan.tenantId(), plan.id(), AuditVerdict.REJECT, "审核拒绝");
                    inboxCommandService.post(plan.tenantId(), "计划审核未通过", "请修改后重新发布", new BizRef("AUDIT_REJECT", plan.id().value()));
                } else if (st.contains("DELIVER") || st.contains("OK") || st.contains("ENABLE") || st.contains("ACTIVE")) {
                    planCommandService.markAudit(plan.tenantId(), plan.id(), AuditVerdict.PASS, null);
                }
            } catch (Exception ex) {
                log.warn("live audit poll failed plan={}", plan.id().value());
            }
        }
    }
}
