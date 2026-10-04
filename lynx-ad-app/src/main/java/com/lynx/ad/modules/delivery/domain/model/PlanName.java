package com.lynx.ad.modules.delivery.domain.model;

import com.lynx.ad.common.exception.DomainException;

public record PlanName(String text) {

    public PlanName {
        if (text == null || text.isBlank() || text.length() > 30) {
            throw new DomainException(1401, "计划名称须 1–30 字");
        }
        text = text.trim();
    }
}
