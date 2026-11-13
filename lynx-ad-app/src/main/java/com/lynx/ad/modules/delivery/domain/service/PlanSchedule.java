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
    public static Schedule read(JsonNode setting, Schedule fallback) {
        var platformType = type(setting);
        var start = QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting, "start_time"));
        if (start == null) start = fallback.startDate();
        var end = QianchuanPlanMapping.date(QianchuanPlanMapping.text(setting, "end_time"));
        if (platformType == ScheduleType.FROM_NOW) return new Schedule(ScheduleType.FROM_NOW, start, null);
        if (end == null) {
            if (platformType == null) return new Schedule(fallback.type(), start, fallback.endDate());
            return new Schedule(ScheduleType.DATE_RANGE, start,
                    fallback.type() == ScheduleType.DATE_RANGE ? fallback.endDate() : null);
        }
        // A list response can omit the type and still return the ten-year placeholder for long-running plans.
        boolean savedCustomEnd = fallback.type() == ScheduleType.DATE_RANGE && end.equals(fallback.endDate());
        if (platformType == null && !savedCustomEnd && start != null && start.plusYears(10).equals(end)) {
            return new Schedule(ScheduleType.FROM_NOW, start, null);
        }
        return new Schedule(ScheduleType.DATE_RANGE, start, end);
    }
}
