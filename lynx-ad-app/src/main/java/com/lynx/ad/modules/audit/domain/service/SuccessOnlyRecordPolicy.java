package com.lynx.ad.modules.audit.domain.service;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.audit.domain.model.AuditActionType;

public final class SuccessOnlyRecordPolicy {

    private SuccessOnlyRecordPolicy() {}

    public static void assertAllowed(AuditActionType type, String objectLabel, boolean success) {
        if (!success) {
            throw new DomainException(1601, "只记录成功操作");
        }
        if (type == null) {
            throw new DomainException(1601, "操作类型不合法");
        }
        if (objectLabel != null
                && (objectLabel.contains("自动调价") || objectLabel.contains("自动优化"))) {
            throw new DomainException(1601, "不得记录未发生的自动调价");
        }
    }
}
