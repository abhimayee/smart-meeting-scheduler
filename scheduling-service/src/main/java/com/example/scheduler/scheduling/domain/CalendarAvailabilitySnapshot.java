package com.example.scheduler.scheduling.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.FreeInterval;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public record CalendarAvailabilitySnapshot(EmailAddress email, ZoneId timeZone, Instant rangeStart,
                                          Instant rangeEnd, List<FreeInterval> freeIntervals) {
    public CalendarAvailabilitySnapshot { freeIntervals = List.copyOf(freeIntervals); }
}
