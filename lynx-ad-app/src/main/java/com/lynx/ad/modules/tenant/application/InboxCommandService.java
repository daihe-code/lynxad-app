package com.lynx.ad.modules.tenant.application;

import com.lynx.ad.common.domain.Ids.NotificationId;
import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.domain.Ids.UserId;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.common.security.RequestMeta;
import com.lynx.ad.modules.audit.application.AuditRecorder;
import com.lynx.ad.modules.audit.application.SuccessAuditCommand;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.tenant.api.dto.InboxMessageVo;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.domain.model.InboxMessage;
import com.lynx.ad.modules.tenant.domain.port.InboxMessageRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InboxCommandService {

    private final InboxMessageRepository repository;
    private final AuditRecorder auditRecorder;

    public InboxCommandService(InboxMessageRepository repository, AuditRecorder auditRecorder) {
        this.repository = repository;
        this.auditRecorder = auditRecorder;
    }

    public void post(TenantId tenantId, String title, String body, BizRef ref) {
        repository.save(InboxMessage.post(tenantId, title, body, ref));
        String label = title == null ? "系统通知" : (title.length() > 32 ? title.substring(0, 32) : title);
        auditRecorder.record(new SuccessAuditCommand(
                tenantId,
                null,
                "系统",
                AuditActionType.SYSTEM_NOTICE,
                "INBOX",
                null,
                label,
                RequestMeta.ip().orElse(null),
                null,
                RequestMeta.userAgent().orElse(null),
                null));
    }

    public void markRead(TenantId tenantId, NotificationId id, UserId reader) {
        InboxMessage msg = repository.findById(tenantId, id).orElseThrow(() -> new BizException(1001, "通知不存在"));
        msg.markRead();
        repository.save(msg);
    }

    public List<InboxMessageVo> list(TenantId tenantId) {
        return repository.listByTenant(tenantId).stream()
                .map(m -> new InboxMessageVo(
                        m.id().value(),
                        m.title(),
                        m.body(),
                        m.ref() == null ? null : m.ref().bizType(),
                        m.ref() == null ? null : m.ref().bizId(),
                        m.unread(),
                        m.sentAt()))
                .toList();
    }
}
