package com.lynx.ad.modules.delivery.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.infrastructure.adapter.QianchuanPlanMapping;

public final class PlanSchedule {
    private PlanSchedule() {}
    public static ScheduleType type(JsonNode setting) {
        return switch(setting.path("video_schedule_type").asText(setting.path("schedule_type").asText())) {
            case "SCHEDULE_FROM_NOW", "FROM_NOW" -> ScheduleType.FROM_NOW;
            case "SCHEDULE_START_END", "DATE_RANGE" -> ScheduleType.DATE_RANGE;
            default -> null;
        };
    }
    public static boolean complete(JsonNode setting) {
        var type=type(setting);
        return type!=null && (type==ScheduleType.FROM_NOW || QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting,"end_time"))!=null);
    }
    public static Schedule read(JsonNode setting,Schedule fallback) {
        var type=type(setting);boolean explicit=type!=null;if(!explicit)type=fallback.type();
        var start=setting.hasNonNull("start_time")?QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting,"start_time")):fallback.startDate();
        var end=explicit && type==ScheduleType.FROM_NOW?null:setting.hasNonNull("end_time")?QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting,"end_time")):fallback.endDate();
        return new Schedule(type,start,end);
    }
}
