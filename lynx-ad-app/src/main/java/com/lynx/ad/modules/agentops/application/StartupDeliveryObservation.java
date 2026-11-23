package com.lynx.ad.modules.agentops.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

/** Counts eligible intervals between real observations, independently of the calendar date. */
record StartupDeliveryObservation(long effectiveMillis, boolean hadVolume, boolean zeroNow, long zeroMillis, long checkLagMillis) {
    private static final long ONE_HOUR = Duration.ofHours(1).toMillis();
    private static final long MAX_SAMPLE_GAP = Duration.ofMinutes(5).toMillis();
    private static final long MAX_CHECK_AGE = Duration.ofMinutes(10).toMillis();

    static StartupDeliveryObservation update(ObjectNode report, JsonNode previous, OffsetDateTime observed, boolean blocked) {
        String origin = report.path("delivery_baseline").asText();
        var prior = previous == null ? null : previous.path("delivery_observation");
        boolean sameRound = prior != null && prior.isObject() && origin.equals(prior.path("origin").asText())
            && report.path("plan_id").equals(previous.path("plan_id"))
            && products(report).equals(products(previous));
        var priorAt = sameRound ? time(prior.path("at")) : null;
        long interval = priorAt == null ? -1 : elapsed(priorAt, observed);
        if (interval < 0) sameRound = false;

        var check = report.path("delivery_check");
        var checkedAt = time(check.path("checked_at"));
        long age = checkedAt == null ? -1 : elapsed(checkedAt, observed);
        var baseline = time(report.path("delivery_baseline"));
        String activity = check.path("activity").asText();
        boolean fresh = age >= 0 && age <= MAX_CHECK_AGE && (baseline == null || !checkedAt.isBefore(baseline));
        boolean eligible = !blocked && "DELIVERING".equals(report.path("plan_status").asText())
            && !report.path("execution_pending").asBoolean() && fresh && ("ACTIVE".equals(activity) || "ZERO".equals(activity));
        boolean continuous = sameRound && interval <= MAX_SAMPLE_GAP && prior.path("eligible").asBoolean() && eligible
            && !report.path("observation_interrupted").asBoolean()
            && report.path("optimization_restarted_at").equals(previous.path("optimization_restarted_at"));
        long effective = sameRound ? prior.path("effective_millis").asLong() : 0;
        if (continuous) effective += interval;

        var history = report.path("delivery_history");
        boolean historicVolume = !origin.isEmpty() && origin.equals(history.path("baseline").asText())
            && observed.toLocalDate().minusDays(1).toString().equals(history.path("through").asText())
            && "ACTIVE".equals(history.path("activity").asText());
        boolean hadVolume = sameRound && prior.path("had_volume").asBoolean() || historicVolume || fresh && "ACTIVE".equals(activity);
        boolean zeroNow = eligible && !hadVolume && "ZERO".equals(activity);
        var zeroSince = zeroNow && continuous ? time(prior.path("zero_since")) : null;
        if (zeroNow && zeroSince == null) zeroSince = observed;

        var snapshot = report.putObject("delivery_observation").put("origin", origin).put("at", observed.toString())
            .put("eligible", eligible).put("effective_millis", effective).put("had_volume", hadVolume);
        if (zeroSince != null) snapshot.put("zero_since", zeroSince.toString());
        long zeroMillis = zeroSince == null ? 0 : elapsed(zeroSince, checkedAt);
        return new StartupDeliveryObservation(effective, hadVolume, zeroNow, zeroMillis, age);
    }

    boolean complete() { return effectiveMillis >= 4 * ONE_HOUR; }
    boolean investigate() { return zeroNow && effectiveMillis - checkLagMillis >= 4 * ONE_HOUR; }
    boolean keepWatching() { return zeroNow && zeroMillis >= ONE_HOUR; }

    private static Set<String> products(JsonNode report) {
        var ids = new HashSet<String>();
        report.path("product_ids").forEach(id -> ids.add(id.asText()));
        return ids;
    }

    private static OffsetDateTime time(JsonNode value) {
        if (!value.isTextual()) return null;
        try { return OffsetDateTime.parse(value.asText()); }
        catch (DateTimeParseException ignored) { return null; }
    }

    private static long elapsed(OffsetDateTime start, OffsetDateTime end) {
        return Duration.between(start, end).toMillis();
    }
}
