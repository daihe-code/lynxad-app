package com.lynx.ad.modules.notify.infrastructure.persistence;

import com.lynx.ad.modules.notify.domain.model.ContactTicket;
import com.lynx.ad.modules.notify.domain.port.ContactTicketRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MpContactTicketRepository implements ContactTicketRepository {

    private final ContactTicketMapper mapper;

    public MpContactTicketRepository(ContactTicketMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(ContactTicket ticket) {
        ContactTicketPo po = new ContactTicketPo();
        po.setId(ticket.id());
        po.setTenantId(ticket.tenantId() == null ? null : ticket.tenantId().value());
        po.setUserId(ticket.userId() == null ? null : ticket.userId().value());
        po.setPhone(ticket.phone().value());
        po.setSource(ticket.source());
        po.setStatus(ticket.status());
        po.setCreatedAt(ticket.createdAt());
        mapper.insert(po);
    }

    @Override
    public Optional<ContactTicket> findById(String id) {
        ContactTicketPo po = mapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(ContactTicket.openFromLanding(
                po.getPhone(),
                po.getTenantId() == null
                        ? Optional.empty()
                        : Optional.of(new com.lynx.ad.common.domain.Ids.TenantId(po.getTenantId())),
                po.getUserId() == null
                        ? Optional.empty()
                        : Optional.of(new com.lynx.ad.common.domain.Ids.UserId(po.getUserId()))));
    }
}
