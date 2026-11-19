package com.lynx.ad.modules.billing.application;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import com.lynx.ad.modules.tenant.infrastructure.persistence.InboxOutboxStore;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Workspace inbox messages contain shared point quantities, with financial details in the authorized ledger. */
@Component
public class PointActivityNotice {
    private final InboxOutboxStore outbox;

    public PointActivityNotice(InboxOutboxStore outbox) {
        this.outbox = outbox;
    }

    void insufficient(String tenant, PointWalletService.Wallet wallet, BigDecimal required) {
        String balance = "当前可用 " + points(wallet.available()) + "。";
        String body = required == null ? balance : "本次需 " + points(required) + "，" + balance;
        enqueue(tenant, "points:shortfall:" + wallet.version(), "POINTS_INSUFFICIENT", "可用积分不足", body);
    }

    void consumed(String tenant, String reservation, BigDecimal amount, BigDecimal available) {
        enqueue(tenant, "points:consumed:" + reservation, "POINTS_CONSUMED", "积分消耗",
                "本次消耗 " + points(amount) + "，剩余 " + points(available) + "。");
    }

    void recharged(String tenant, String entry, BigDecimal amount, BigDecimal available) {
        enqueue(tenant, "points:recharged:" + entry, "POINTS_RECHARGED", "积分充值到账",
                "已到账 " + points(amount) + "，当前可用 " + points(available) + "。");
    }

    private void enqueue(String tenant, String key, String type, String title, String body) {
        outbox.enqueue(new TenantId(tenant), key, title, body, new BizRef(type, null), Instant.now());
    }

    private static String points(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString() + " 积分";
    }
}
