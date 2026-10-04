package com.lynx.ad.modules.dataops.domain.service;

import com.lynx.ad.modules.dataops.domain.model.AuditVerdict;

public class AuditMappingPolicy {

    public AuditVerdict verdictOf(String rawQcStatus) {
        if (rawQcStatus == null) {
            return AuditVerdict.PENDING;
        }
        String s = rawQcStatus.toUpperCase();
        if (s.contains("PASS") || s.contains("DELIVER") || s.contains("ENABLE") || "OK".equals(s)) {
            return AuditVerdict.PASS;
        }
        if (s.contains("REJECT") || s.contains("FAIL")) {
            return AuditVerdict.REJECT;
        }
        if (s.contains("PAUSE") || s.contains("DISABLE")) {
            return AuditVerdict.ABNORMAL_PAUSE;
        }
        return AuditVerdict.PENDING;
    }
}
