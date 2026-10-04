package com.lynx.ad.modules.notify.domain.port;

import com.lynx.ad.modules.notify.domain.model.ContactTicket;
import java.util.Optional;

public interface ContactTicketRepository {

    void save(ContactTicket ticket);

    Optional<ContactTicket> findById(String id);
}
