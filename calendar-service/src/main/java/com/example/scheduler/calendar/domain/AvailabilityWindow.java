package com.example.scheduler.calendar.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.FreeInterval;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public record AvailabilityWindow(EmailAddress user, ZoneId timeZone, Instant rangeStart, Instant rangeEnd,
                                 List<FreeInterval> freeIntervals) {
    public AvailabilityWindow {
        if (user == null || timeZone == null || rangeStart == null || rangeEnd == null || !rangeStart.isBefore(rangeEnd))
            throw new IllegalArgumentException("User, time zone, and increasing availability range are required");
        freeIntervals = freeIntervals == null ? List.of() : List.copyOf(freeIntervals);
    }
}
