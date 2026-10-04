package com.lynx.ad.modules.notify.infrastructure;

import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.notify.domain.model.ContactTicket;
import com.lynx.ad.modules.notify.domain.port.OpsNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogOpsNotifier implements OpsNotifier {

    private static final Logger log = LoggerFactory.getLogger(LogOpsNotifier.class);

    private final LynxAdProperties properties;

    public LogOpsNotifier(LynxAdProperties properties) {
        this.properties = properties;
    }

    @Override
    public void notify(ContactTicket ticket) {
        String phone = ticket.phone().value();
        String masked = phone.substring(0, 3) + "****" + phone.substring(7);
        String ops = properties.getContact().getOpsPhones();
        int n = 0;
        if (ops != null && !ops.isBlank()) {
            n = ops.split(",").length;
        }
        log.info("contact ticket {} phone={} configured-ops={}", ticket.id(), masked, n);
    }
}
