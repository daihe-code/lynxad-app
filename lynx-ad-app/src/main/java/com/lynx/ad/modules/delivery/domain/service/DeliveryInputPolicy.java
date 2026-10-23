package com.lynx.ad.modules.delivery.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.SmartBidType;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/** Write validation only: platform snapshots may contain valid historical settings outside these profiles. */
public final class DeliveryInputPolicy {
    public record Profile(BigDecimal budgetMaximum, BigDecimal volumeBudgetMinimum, BigDecimal volumeBudgetMaximum, BigDecimal roiMinimum, BigDecimal roiMaximum) {}
    public record Rules(BigDecimal costBudgetMinimum, BigDecimal volumeBudgetMinimum, Profile creation, Profile update,
                        int decimalPlaces, int nameMaxLength, int customTitleMaximum, int titleMaxLength,
                        int titlePlatformMinimum, int titlePlatformMaximum) {}
    public static final Rules RULES=load();
    private DeliveryInputPolicy() {}
    private static Rules load() {
        try(var in=DeliveryInputPolicy.class.getResourceAsStream("/delivery-input-policy.json")) {
            if(in==null)throw new IllegalStateException("Missing delivery input policy");
            return new ObjectMapper().readValue(in,Rules.class);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    public static SmartBidType mode(String value) {
        return switch(value==null?"":value) {
            case "CUSTOM","SMART_BID_CUSTOM" -> SmartBidType.CUSTOM;
            case "CONSERVATIVE","SMART_BID_CONSERVATIVE" -> SmartBidType.CONSERVATIVE;
            default -> throw new BizException(1402,"请选择控成本或放量投放");
        };
    }
    public static BigDecimal minimum(SmartBidType mode) {return mode==SmartBidType.CUSTOM?RULES.costBudgetMinimum():RULES.volumeBudgetMinimum();}
    public static void budget(BigDecimal value,SmartBidType mode,Profile profile) {
        var minimum=mode==SmartBidType.CUSTOM?RULES.costBudgetMinimum():profile.volumeBudgetMinimum();
        if(value!=null && value.compareTo(minimum)<0)
            throw new BizException(1402,(mode==SmartBidType.CUSTOM?"控成本":"放量")+"日预算不能低于 "+minimum.toPlainString()+" 元（含 "+minimum.toPlainString()+" 元）");
        number(value,minimum,mode==SmartBidType.CUSTOM?profile.budgetMaximum():profile.volumeBudgetMaximum(),"日预算");
    }
    public static void roi(BigDecimal value,Profile profile) {number(value,profile.roiMinimum(),profile.roiMaximum(),"净成交ROI目标");}
    public static void settings(SmartBidType mode,BigDecimal budget,BigDecimal roi,Profile profile) {
        budget(budget,mode,profile);
        if(mode==SmartBidType.CUSTOM)roi(roi,profile);
        else if(roi!=null)throw new BizException(1402,"放量投放不设置净成交ROI目标");
    }
    private static void number(BigDecimal value,BigDecimal min,BigDecimal max,String label) {
        if(value==null || value.stripTrailingZeros().scale()>RULES.decimalPlaces() || value.compareTo(min)<0 || value.compareTo(max)>0)
            throw new BizException(1402,label+"范围为 "+min.toPlainString()+"–"+max.toPlainString()+"，最多两位小数");
    }
    /** Matches browser String.trim, including non-breaking spaces and the optional BOM. */
    public static String text(String value) {return value==null?"":value.replaceAll("^[\\x09-\\x0D\\x20\\p{Z}\\uFEFF]+|[\\x09-\\x0D\\x20\\p{Z}\\uFEFF]+$","");}
    public static String name(String value) {
        String name=text(value);
        if(name.isEmpty()||name.length()>RULES.nameMaxLength())throw new BizException(1402,"计划名称须为 1–"+RULES.nameMaxLength()+" 字");
        return name;
    }
    public static void schedule(String type,String start,String end) {
        if("FROM_NOW".equals(type))return;
        if(!"DATE_RANGE".equals(type))throw new BizException(1402,"投放日期类型不合法");
        try {
            if(start==null||end==null||!start.matches("\\d{4}-\\d{2}-\\d{2}")||!end.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();
            var first=LocalDate.parse(start);var last=LocalDate.parse(end);
            if(first.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")))||last.isBefore(first))throw new IllegalArgumentException();
        }catch(Exception e){throw new BizException(1402,"开始日期不得早于今天，结束日期不得早于开始日期");}
    }
    public static int platformLength(String text) {return text.codePoints().map(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN?2:1).sum();}
    public static void titles(List<String> titles,boolean custom) {
        if(titles==null||titles.isEmpty() || (custom&&titles.size()>RULES.customTitleMaximum()))throw new BizException(1503,"请填写 1–"+RULES.customTitleMaximum()+" 条自定义标题");
        for(String raw:titles) {
            String t=text(raw);int length=platformLength(t);
            if(t.isEmpty()||t.length()>RULES.titleMaxLength()||length<RULES.titlePlatformMinimum()||length>RULES.titlePlatformMaximum())
                throw new BizException(1503,"素材标题须满足千川的 10–110 字符要求（汉字按 2 个字符计算，最多 55 字）");
        }
    }
}
