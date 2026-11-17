package com.example.scheduler.shared.domain;

import java.time.Instant;

/** A half-open UTC instant interval [start, end). */
public record InstantRange(Instant start, Instant end) {
    public InstantRange {
        if (start == null || end == null) throw new IllegalArgumentException("Range endpoints are required");
        if (!start.isBefore(end)) throw new IllegalArgumentException("Range start must be before end");
    }
}
