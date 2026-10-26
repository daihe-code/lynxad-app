package com.lynx.ad.modules.delivery.domain.model;

import com.lynx.ad.common.exception.BizException;
import java.util.List;

public final class PlanPauseReason {
    private PlanPauseReason() {}

    public static final List<String> OPTIONS = List.of(
            "预算消耗过快",
            "净成交 ROI 低于预期",
            "商品库存不足或临时缺货",
            "需要更换素材创意",
            "需要调整投放时段",
            "商品价格或活动调整中",
            "账号或资质问题排查中",
            "其他原因");

    public static String require(String reason) {
        if (!OPTIONS.contains(reason == null ? "" : reason))
            throw new BizException(1402, "请选择有效的暂停原因");
        return reason;
    }
}
