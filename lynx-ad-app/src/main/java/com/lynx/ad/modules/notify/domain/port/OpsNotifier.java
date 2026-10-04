package com.lynx.ad.modules.notify.domain.port;

import com.lynx.ad.modules.notify.domain.model.ContactTicket;

public interface OpsNotifier {

    void notify(ContactTicket ticket);
}
