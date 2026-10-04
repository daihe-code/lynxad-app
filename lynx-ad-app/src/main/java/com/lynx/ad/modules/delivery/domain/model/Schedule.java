package com.lynx.ad.modules.delivery.domain.model;

import java.time.LocalDate;

public record Schedule(ScheduleType type, LocalDate startDate, LocalDate endDate) {

    public static Schedule fromNow() {
        return new Schedule(ScheduleType.FROM_NOW, LocalDate.now(), null);
    }
}
