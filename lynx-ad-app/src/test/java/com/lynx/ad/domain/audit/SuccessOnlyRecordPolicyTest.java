package com.lynx.ad.domain.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;
import com.lynx.ad.modules.audit.domain.service.SuccessOnlyRecordPolicy;
import org.junit.jupiter.api.Test;

class SuccessOnlyRecordPolicyTest {

    @Test
    void rejectsFailureAndAutoBid() {
        DomainException fail = assertThrows(
                DomainException.class, () -> SuccessOnlyRecordPolicy.assertAllowed(AuditActionType.LOGIN, "登录", false));
        assertEquals(1601, fail.code());
        DomainException auto = assertThrows(
                DomainException.class,
                () -> SuccessOnlyRecordPolicy.assertAllowed(AuditActionType.PLAN, "自动调价", true));
        assertEquals(1601, auto.code());
        SuccessOnlyRecordPolicy.assertAllowed(AuditActionType.LOGIN, "登录", true);
    }
}
