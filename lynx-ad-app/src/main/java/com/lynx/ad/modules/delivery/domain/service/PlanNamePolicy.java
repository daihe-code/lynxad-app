package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.common.exception.DomainException;
import com.lynx.ad.modules.delivery.domain.model.PlanName;
import java.time.LocalDate;

public class PlanNamePolicy {

    public PlanName suggest(String productNameSummary, LocalDate date) {
        String day = date == null ? LocalDate.now().toString() : date.toString();
        String base = (productNameSummary == null || productNameSummary.isBlank() ? "商品推广" : productNameSummary.trim())
                + "-"
                + day;
        if (base.length() > 30) {
            base = base.substring(0, 30);
        }
        return new PlanName(base);
    }

    public void assertUnique(boolean existsUnfinishedDuplicate) {
        if (existsUnfinishedDuplicate) {
            throw new DomainException(1401, "同一抖音号下未结束计划名称不可重复");
        }
    }
}
