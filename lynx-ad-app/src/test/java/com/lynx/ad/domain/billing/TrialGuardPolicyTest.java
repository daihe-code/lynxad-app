package com.lynx.ad.domain.billing;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lynx.ad.common.domain.Ids.TenantId;
import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.billing.domain.model.PlanCode;
import com.lynx.ad.modules.billing.domain.model.SubStatus;
import com.lynx.ad.modules.billing.domain.model.Subscription;
import com.lynx.ad.modules.billing.domain.model.Trial;
import com.lynx.ad.modules.billing.domain.model.TrialWindow;
import com.lynx.ad.modules.billing.domain.model.WriteAction;
import com.lynx.ad.modules.billing.domain.service.TrialGuardPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrialGuardPolicyTest {

    private final TrialGuardPolicy policy = new TrialGuardPolicy();

    @Test
    void expiredBlocksWriteAllowsRead() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Trial trial = Trial.start(new TenantId("t1"), start, TrialWindow.threeDays());
        Instant expired = start.plusSeconds(3 * 24 * 3600);
        assertTrue(trial.isActive(expired.minusSeconds(1)));
        assertTrue(trial.isExpired(expired));
        assertFalse(trial.isActive(expired));
        assertThrows(DomainException.class, () -> policy.assertWritable(trial, expired, WriteAction.PUBLISH));
        assertTrue(trial.isActive(start.plusSeconds(3600)));
    }

    @Test
    void activeAllowsWrite() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Trial trial = Trial.start(new TenantId("t1"), now, TrialWindow.threeDays());
        assertDoesNotThrow(() -> policy.assertWritable(trial, now.plusSeconds(60), WriteAction.SAVE_PLAN));
        assertEquals(1904, assertThrows(DomainException.class,
                () -> policy.assertWritable(trial, now.plusSeconds(60), WriteAction.GENERATE_CREATIVE)).code());
    }

    @Test
    void expiredWithoutSubscriptionBlocksWithNewCopy() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Trial trial = Trial.start(new TenantId("t1"), start, TrialWindow.threeDays());
        Instant expired = start.plusSeconds(3 * 24 * 3600);
        DomainException ex = assertThrows(
                DomainException.class, () -> policy.assertWritable(trial, null, expired, WriteAction.PUBLISH));
        assertEquals(1102, ex.code());
        assertTrue(ex.getMessage().contains("无有效订阅"));
    }

    @Test
    void expiredTrialWithActiveSubscriptionAllowsWrite() {
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        Trial trial = Trial.start(new TenantId("t1"), Instant.parse("2026-01-01T00:00:00Z"), TrialWindow.threeDays());
        Subscription sub = new Subscription(
                new TenantId("t1"),
                PlanCode.PROFESSIONAL,
                SubStatus.ACTIVE,
                now.minusSeconds(3600),
                now.plusSeconds(86400),
                true,
                "WECHAT",
                71900,
                null,
                now);
        assertTrue(trial.isExpired(now));
        assertDoesNotThrow(() -> policy.assertWritable(trial, sub, now, WriteAction.PUBLISH));
        assertDoesNotThrow(() -> policy.assertWritable(trial, sub, now, WriteAction.GENERATE_CREATIVE));
    }
}
