package com.example.scheduler.shared.domain;

import java.time.Instant;

/** A half-open interval [start, end) during which a user is free. */
public record FreeInterval(Instant start, Instant end) {
    public FreeInterval {
        if (start == null || end == null || !start.isBefore(end))
            throw new IllegalArgumentException("Free interval start must be before end");
    }
}
