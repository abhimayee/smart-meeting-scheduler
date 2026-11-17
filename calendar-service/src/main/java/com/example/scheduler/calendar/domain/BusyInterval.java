package com.example.scheduler.calendar.domain;

import java.time.Instant;

/** A half-open interval [start, end) during which a user is busy. */
public record BusyInterval(Instant start, Instant end) {
    public BusyInterval {
        if (start == null || end == null || !start.isBefore(end))
            throw new IllegalArgumentException("Busy interval start must be before end");
    }
}
