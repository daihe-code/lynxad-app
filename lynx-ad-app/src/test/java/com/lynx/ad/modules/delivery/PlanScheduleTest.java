package com.lynx.ad.modules.delivery;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynx.ad.modules.delivery.domain.model.*;
import com.lynx.ad.modules.delivery.domain.service.PlanSchedule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanScheduleTest {
    final ObjectMapper json=new ObjectMapper();
    final Schedule saved=new Schedule(ScheduleType.DATE_RANGE,LocalDate.of(2026,10,19),LocalDate.of(2026,12,1));
    @Test void longTermIgnoresPlatformPlaceholderEndWhileFixedDatesRemainExact()throws Exception {
        var longTerm=json.readTree("{\"video_schedule_type\":\"SCHEDULE_FROM_NOW\",\"end_time\":\"2036-10-19 23:59:59\"}");
        assertNull(PlanSchedule.read(longTerm,saved).endDate());assertEquals(saved.startDate(),PlanSchedule.read(longTerm,saved).startDate());
        var custom=json.readTree("{\"video_schedule_type\":\"SCHEDULE_START_END\",\"end_time\":\"2026-11-01 23:59:59\"}");
        assertTrue(PlanSchedule.complete(custom));assertEquals(LocalDate.of(2026,11,1),PlanSchedule.read(custom,saved).endDate());
    }
    @Test void incompleteOrNewPlatformScheduleDoesNotEraseExistingDatesOrBecomeConfirmed()throws Exception {
        for(String raw:new String[]{"{}","{\"video_schedule_type\":\"NEW_TYPE\"}","{\"video_schedule_type\":\"SCHEDULE_START_END\",\"end_time\":\"invalid\"}"})assertFalse(PlanSchedule.complete(json.readTree(raw)));
        assertEquals(saved,PlanSchedule.read(json.createObjectNode(),saved));
    }
    @Test void tenYearEndWithoutTypeUsesSavedCustomDatesWhenAvailable() {
        var start = LocalDate.of(2026, 10, 19);
        var end = start.plusYears(10);
        var setting = json.createObjectNode().put("start_time", start.toString()).put("end_time", end.toString());
        var custom = new Schedule(ScheduleType.DATE_RANGE, start, end);
        var longTerm = new Schedule(ScheduleType.FROM_NOW, start, null);

        assertEquals(longTerm, PlanSchedule.read(setting, longTerm));
        assertEquals(custom, PlanSchedule.read(setting, custom));
        assertFalse(PlanSchedule.complete(setting));
        setting.put("video_schedule_type", "SCHEDULE_START_END");
        assertEquals(custom, PlanSchedule.read(setting, longTerm));
    }
}
