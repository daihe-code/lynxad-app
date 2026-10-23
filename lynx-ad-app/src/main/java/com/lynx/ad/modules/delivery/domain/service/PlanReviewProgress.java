package com.lynx.ad.modules.delivery.domain.service;

import java.time.*;

/** Review waiting and read failures never imply rejection or permit another publish. */
public final class PlanReviewProgress {
    private PlanReviewProgress() {}
    public static final Duration DELAY_AFTER=Duration.ofMinutes(15);
    public static Duration interval(Instant since,Instant now) {
        return since!=null && !since.plus(DELAY_AFTER).isAfter(now)?Duration.ofMinutes(3):Duration.ofSeconds(30);
    }
    public record View(String state,Instant pendingSince,Instant lastCheckedAt,Instant nextCheckAt,
                       String lastResult,boolean checking,Instant manualRefreshAt,String message) {}
    public static View view(Instant since,Instant checked,String result,Instant next,Instant lease,Instant attempted,Instant now) {
        boolean delayed=interval(since,now).equals(Duration.ofMinutes(3));
        String state="FAILED".equals(result)||"NOT_FOUND".equals(result)||"UNKNOWN".equals(result)?"CHECK_UNAVAILABLE":delayed?"DELAYED":"WAITING";
        String message=switch(state) {
            case "CHECK_UNAVAILABLE" -> "暂未取得最新审核结果，保留上次审核状态。系统会继续查询，也可稍后手动刷新；请勿重复发布。";
            case "DELAYED" -> "本轮审核已等待超过15分钟，平台仍未返回最终结果。系统已降低查询频率；这不代表审核失败，无需重复发布，可继续其他操作。";
            default -> "计划正在等待平台审核，结果以千川返回为准。可继续其他操作，无需重复发布。";
        };
        return new View(state,since,checked,next,result,lease!=null&&lease.isAfter(now),attempted==null?null:attempted.plusSeconds(30),message);
    }
}
