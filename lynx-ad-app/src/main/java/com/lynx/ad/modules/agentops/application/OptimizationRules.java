package com.lynx.ad.modules.agentops.application;

import com.lynx.ad.common.exception.BizException;
import java.math.BigDecimal;
import java.util.Set;

/** Merchant-facing rules. Addition limits are advice limits, never spend authorization. */
public record OptimizationRules(BigDecimal roiFloor, BigDecimal spendReminder, BigDecimal spendProtection,
        String protectionAction, int runawayMinutes, BigDecimal runawaySpend,
        BigDecimal maxAddition, BigDecimal dailyAddition) {
    public static final String CONFIRM = "CONFIRM", AUTO_PAUSE = "AUTO_PAUSE";
    public void validate() {
        amount(roiFloor,"期望净成交ROI",new BigDecimal("0.01"),new BigDecimal("100"));
        amount(spendReminder,"当天消耗提醒金额",new BigDecimal("0.01"),new BigDecimal("999999999.99"));
        amount(spendProtection,"当天消耗保护金额",new BigDecimal("0.01"),new BigDecimal("999999999.99"));
        if(spendReminder.compareTo(spendProtection)>0)throw new BizException(1802,"消耗保护金额不能低于提醒金额");
        if(!Set.of(CONFIRM,AUTO_PAUSE).contains(protectionAction==null?"":protectionAction))throw new BizException(1802,"请选择达到保护金额后的处理方式");
        if(runawayMinutes<1 || runawayMinutes>60)throw new BizException(1802,"跑飞观察时间须为1–60分钟");
        amount(runawaySpend,"跑飞提醒消耗",new BigDecimal("0.01"),new BigDecimal("999999999.99"));
        amount(maxAddition,"单次追加预算",new BigDecimal("0.01"),new BigDecimal("999999999.99"));
        amount(dailyAddition,"每日追加预算",new BigDecimal("0.01"),new BigDecimal("999999999.99"));
        if(maxAddition.compareTo(dailyAddition)>0)throw new BizException(1802,"每天累计追加预算不能低于单次追加预算");
    }
    public OptimizationPolicy policy(boolean enabled) {
        var defaults = OptimizationPolicy.defaults();
        return new OptimizationPolicy(enabled, "OBSERVE", spendProtection, defaults.maxLoss(),
            defaults.minSpend(), defaults.minOrders(), defaults.persistenceWindows(),
            defaults.conversionDelayMinutes(), java.util.List.of(), null, null, null, this);
    }

    public boolean restricts(OptimizationRules previous) {
        return AUTO_PAUSE.equals(previous.protectionAction()) &&
                (!AUTO_PAUSE.equals(protectionAction) || spendProtection.compareTo(previous.spendProtection()) != 0)
            || roiFloor.compareTo(previous.roiFloor()) > 0
            || spendReminder.compareTo(previous.spendReminder()) < 0
            || maxAddition.compareTo(previous.maxAddition()) < 0
            || dailyAddition.compareTo(previous.dailyAddition()) < 0;
    }

    private static void amount(BigDecimal value,String label,BigDecimal min,BigDecimal max) {
        if(value==null || value.compareTo(min)<0 || value.compareTo(max)>0 || value.stripTrailingZeros().scale()>2)
            throw new BizException(1802,label+"超出范围，最多两位小数");
    }
}
