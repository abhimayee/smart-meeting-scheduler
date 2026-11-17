package com.example.scheduler.shared.domain;

import java.time.LocalTime;

/** A half-open local time interval [start, end); overnight ranges are not supported in Phase 1. */
public record TimeRange(LocalTime start, LocalTime end) {
    public TimeRange {
        if (start == null || end == null) throw new IllegalArgumentException("Range endpoints are required");
        if (!start.isBefore(end)) throw new IllegalArgumentException("Range start must be before end");
    }
}
