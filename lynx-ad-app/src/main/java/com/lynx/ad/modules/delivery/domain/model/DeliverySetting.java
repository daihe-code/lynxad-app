package com.lynx.ad.modules.delivery.domain.model;

import com.lynx.ad.common.domain.Money;
import com.lynx.ad.common.exception.DomainException;
import java.util.Optional;

public record DeliverySetting(
        SmartBidType bidType, Money dailyBudget, Optional<RoiGoal> roi2Goal, Schedule schedule, AudienceJson audience) {

    public void assertReadyToPublish() {
        if (bidType == null) {
            throw new DomainException(1402, "请选择投放方式");
        }
        if (dailyBudget == null || dailyBudget.amount().signum() <= 0) {
            throw new DomainException(1402, "请设置日预算");
        }
        if (bidType == SmartBidType.CUSTOM && (roi2Goal == null || roi2Goal.isEmpty())) {
            throw new DomainException(1402, "控成本须设置 ROI 目标");
        }
    }
}
