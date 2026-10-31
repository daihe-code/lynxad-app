package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** Only an unsubmitted creation draft may move its expired dates forward. */
public final class CreationDraftSchedule {
    private CreationDraftSchedule() {}
    public static Schedule correction(CreationState state,LocalDate today) {
        if(state.publishedEdit!=null||state.qcPlanId!=null||!Set.of("NONE","FAILED").contains(state.publishStatus)
                ||!"DATE_RANGE".equals(state.scheduleType))return null;
        try {
            if(state.startDate==null||state.endDate==null||!state.startDate.matches("\\d{4}-\\d{2}-\\d{2}")||!state.endDate.matches("\\d{4}-\\d{2}-\\d{2}"))return null;
            var start=LocalDate.parse(state.startDate);var end=LocalDate.parse(state.endDate);
            if(!start.isBefore(today)||end.isBefore(start))return null;
            return new Schedule(ScheduleType.DATE_RANGE,today,end.isBefore(today)?today.plusDays(ChronoUnit.DAYS.between(start,end)):end);
        }catch(java.time.DateTimeException invalid){return null;}
    }
}
