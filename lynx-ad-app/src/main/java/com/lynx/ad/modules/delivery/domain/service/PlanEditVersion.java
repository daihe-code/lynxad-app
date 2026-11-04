package com.lynx.ad.modules.delivery.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.common.exception.BizException;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bind the edit to business settings, never to fluctuating report metrics or expiring media URLs. */
public final class PlanEditVersion {
    private PlanEditVersion() {}
    private static String amount(BigDecimal value){return value==null?null:value.stripTrailingZeros().toPlainString();}
    private static List<Object> fields(AdPlan p,String name,String status,String mode,BigDecimal budget,BigDecimal roi) {
        var schedule=p.setting().schedule();
        return Arrays.asList(p.tenantId().value(),p.id().value(),p.accountId().value(),p.awemeId().value(),p.qcPlanId().map(x->x.value()).orElse(null),
            name,status,mode,amount(budget),amount(roi),Arrays.asList(schedule.type().name(),schedule.startDate()==null?null:schedule.startDate().toString(),schedule.endDate()==null?null:schedule.endDate().toString()),p.productIds().stream().map(x->x.value()).sorted().toList());
    }
    public static String snapshot(AdPlan p) {
        return hash(fields(p,p.name().text(),p.status().name(),p.setting().bidType().name(),p.setting().dailyBudget().amount(),p.setting().roi2Goal().map(RoiGoal::value).orElse(null)));
    }
    public static String version(AdPlan p,long revision){return revision+"."+snapshot(p);}
    public static void require(String expected,AdPlan p,long revision) {
        if(expected==null || !MessageDigest.isEqual(version(p,revision).getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8)))
            throw new BizException(1404,"计划已在其他位置更新，或页面版本已过期，请重新读取并核对；当前输入可保留");
    }
    public static void platformUnchanged(AdPlan p,JsonNode detail) {
        platformUnchanged(p,detail,true);
    }
    /** Dedicated settings updates must not depend on dates that they do not write. */
    public static void platformUnchanged(AdPlan p,JsonNode detail,boolean compareSchedule) {
        var ds=detail.path("delivery_setting");
        if(validMode(ds)&&SmartBidType.fromOcean(ds.path("smart_bid_type").asText())!=p.setting().bidType())
            throw new BizException(1404,"千川上的计划投放方式已变化，请先同步最新设置并核对；当前输入可保留");
        if(!p.qcPlanId().orElseThrow().value().equals(detail.path("ad_id").asText()) || !detail.hasNonNull("name") || !detail.hasNonNull("status") || !ds.hasNonNull("budget") || !validMode(ds) || SmartBidType.fromOcean(ds.path("smart_bid_type").asText())==SmartBidType.CUSTOM&&!ds.has("roi2_goal"))
            throw new BizException(1406,"千川未返回完整计划设置，暂不提交修改，请重新读取并核对");
        if(compareSchedule && PlanSchedule.complete(ds)) {
            var before=p.setting().schedule();var actual=PlanSchedule.read(ds,before);
            if(before.type()!=actual.type() || !Objects.equals(before.startDate(),actual.startDate()) || !Objects.equals(before.endDate(),actual.endDate()))
                throw new BizException(1404,"千川上的投放日期已变化，请先同步最新设置并核对；当前输入可保留");
        }
        var status=platformStatus(detail);
        String actual=hash(fields(p,DeliveryInputPolicy.text(detail.path("name").asText()),status.name(),SmartBidType.fromOcean(ds.path("smart_bid_type").asText()).name(),
            QianchuanPlanMapping.number(ds,"budget"),QianchuanPlanMapping.number(ds,"roi2_goal")));
        if(!snapshot(p).equals(actual))throw new BizException(1404,"千川上的计划名称、状态、预算或 ROI 已变化，请先同步最新设置并核对；当前输入可保留");
    }
    public static boolean validMode(JsonNode ds){return Set.of("SMART_BID_CUSTOM","SMART_BID_CONSERVATIVE","CUSTOM","CONSERVATIVE").contains(ds.path("smart_bid_type").asText());}
    public static PlanStatus platformStatus(JsonNode detail) {
        var status=QianchuanPlanMapping.status(detail.path("status").asText());
        return (status==PlanStatus.DELIVERING||status==PlanStatus.PAUSED)&&"DISABLE".equals(detail.path("opt_status").asText())?PlanStatus.PAUSED:status;
    }
    private static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().writeValueAsBytes(value)));
        }catch(Exception ex){throw new IllegalStateException("Cannot identify plan edit version",ex);}
    }
}
