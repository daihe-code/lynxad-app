package com.lynx.ad.modules.billing.domain.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public class PeriodPolicy {

    public record Period(Instant start, Instant end, LocalDate startDate, LocalDate endDate) {}

    private final ZoneId zone;

    public PeriodPolicy(ZoneId zone) {
        this.zone = zone == null ? ZoneId.of("Asia/Shanghai") : zone;
    }

    public PeriodPolicy() {
        this(ZoneId.of("Asia/Shanghai"));
    }

    public ZoneId zone() {
        return zone;
    }

    public Period fromPaidAt(Instant paidAt) {
        LocalDate startDate = LocalDate.ofInstant(paidAt, zone);
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        Instant start = startDate.atStartOfDay(zone).toInstant();
        Instant end = endDate.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant();
        return new Period(start, end, startDate, endDate);
    }

    public int remainingDays(Instant now, Instant periodEnd) {
        if (now == null || periodEnd == null || now.isAfter(periodEnd)) {
            return 0;
        }
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate endDate = LocalDate.ofInstant(periodEnd, zone);
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, endDate) + 1);
    }

    public LocalDate nextDebitDate(Instant periodEnd) {
        return LocalDate.ofInstant(periodEnd, zone).plusDays(1);
    }
}
