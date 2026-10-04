package com.lynx.ad.modules.notify.application;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.notify.api.dto.ContactInfoVo;
import com.lynx.ad.modules.notify.api.dto.ContactTicketVo;
import com.lynx.ad.modules.notify.domain.model.ContactTicket;
import com.lynx.ad.modules.notify.domain.port.ContactTicketRepository;
import com.lynx.ad.modules.notify.domain.port.OpsNotifier;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotifyContactService {

    private static final Logger log = LoggerFactory.getLogger(NotifyContactService.class);

    private final ContactTicketRepository repository;
    private final OpsNotifier opsNotifier;
    private final LynxAdProperties properties;

    public NotifyContactService(
            ContactTicketRepository repository, OpsNotifier opsNotifier, LynxAdProperties properties) {
        this.repository = repository;
        this.opsNotifier = opsNotifier;
        this.properties = properties;
    }

    public ContactInfoVo info() {
        return new ContactInfoVo(properties.getContact().getPhone(), properties.getContact().getEmail());
    }

    public ContactTicketVo submit(String phone) {
        ContactTicket ticket = ContactTicket.openFromLanding(
                phone,
                TenantContext.tenantId(),
                optionalUser());
        repository.save(ticket);
        try {
            opsNotifier.notify(ticket);
        } catch (Exception ex) {
            log.error("ops notify failed ticket={}", ticket.id(), ex);
        }
        return new ContactTicketVo(ticket.id());
    }

    private static Optional<com.lynx.ad.common.domain.Ids.UserId> optionalUser() {
        try {
            return Optional.of(TenantContext.userId());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
