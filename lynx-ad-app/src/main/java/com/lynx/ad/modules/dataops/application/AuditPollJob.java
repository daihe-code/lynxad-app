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
                postAuditNotice(plan, verdict);
            }
            return;
        }
        java.util.Map<String,List<OceanPlanRow>> accountRows = new java.util.HashMap<>();
        java.util.Set<String> failedAccounts = new java.util.HashSet<>();
        for (AdPlan plan : planRepository.findPendingAudit()) {
            if (plan.qcPlanId().isEmpty() || plan.source() == com.lynx.ad.modules.delivery.domain.model.PlanSource.EXTERNAL) {
                continue;
            }
            try {
                String accountKey = plan.tenantId().value()+":"+plan.accountId().value();
                if (failedAccounts.contains(accountKey)) continue;
                List<OceanPlanRow> rows;
                try { rows = accountRows.computeIfAbsent(accountKey, key -> adListPort.list(
                        credentialFacade.getUsableCredential(plan.tenantId(), plan.accountId()), null)); }
                catch (RuntimeException ex) { failedAccounts.add(accountKey); throw ex; }
                OceanPlanRow match = rows.stream()
                        .filter(r -> plan.qcPlanId().get().value().equals(r.qcPlanId()))
                        .findFirst()
                        .orElse(null);
                if (match == null) {
                    continue;
                }
                String st = match.status() == null ? "" : match.status().toUpperCase();
                if (com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping.status(st) == com.lynx.ad.modules.delivery.domain.model.PlanStatus.REJECTED) {
                    planCommandService.markAudit(plan.tenantId(), plan.id(), AuditVerdict.REJECT, "审核拒绝");
                    postAuditNotice(plan, AuditVerdict.REJECT);
                } else if (com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping.status(st) == com.lynx.ad.modules.delivery.domain.model.PlanStatus.DELIVERING) {
                    planCommandService.markAudit(plan.tenantId(), plan.id(), AuditVerdict.PASS, null);
                    postAuditNotice(plan, AuditVerdict.PASS);
                }
            } catch (Exception ex) {
                log.warn("live audit poll failed plan={}", plan.id().value());
            }
        }
    }

    private void postAuditNotice(AdPlan plan, AuditVerdict verdict) {
        if (verdict == AuditVerdict.PASS) inboxCommandService.post(plan.tenantId(), "计划审核通过",
                "你提交的广告投放计划「" + plan.name().text() + "」已通过平台审核，正在投放中。小灵会持续关注它的表现。", new BizRef("AUDIT_PASS", plan.id().value()));
        else if (verdict == AuditVerdict.REJECT) inboxCommandService.post(plan.tenantId(), "计划审核未通过",
                "广告投放计划「" + plan.name().text() + "」审核未通过，请查看原因并修改后重新发布。", new BizRef("AUDIT_REJECT", plan.id().value()));
    }
}
