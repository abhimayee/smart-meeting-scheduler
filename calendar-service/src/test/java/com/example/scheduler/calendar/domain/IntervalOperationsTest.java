package com.example.scheduler.calendar.domain;

import com.example.scheduler.shared.domain.FreeInterval;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IntervalOperationsTest {
    private final IntervalOperations operations = new IntervalOperations();
    private Instant at(String value) { return Instant.parse("2026-10-05T" + value + ":00Z"); }

    @Test void mergesOverlappingAndAdjacentBusyIntervalsInSortedOrder() {
        var merged = operations.mergeBusyIntervals(List.of(
                new BusyInterval(at("11:00"), at("12:00")),
                new BusyInterval(at("09:00"), at("10:30")),
                new BusyInterval(at("10:30"), at("11:00"))));
        assertEquals(List.of(new BusyInterval(at("09:00"), at("12:00"))), merged);
    }

    @Test void subtractsBusyIntervalsUsingHalfOpenBoundaries() {
        var free = operations.subtractBusyIntervals(
                List.of(new FreeInterval(at("09:00"), at("12:00"))),
                List.of(new BusyInterval(at("10:00"), at("11:00"))));
        assertEquals(List.of(new FreeInterval(at("09:00"), at("10:00")),
                new FreeInterval(at("11:00"), at("12:00"))), free);
    }

    @Test void adjacentBusyIntervalsLeaveNoFalseFreeGap() {
        var free = operations.subtractBusyIntervals(
                List.of(new FreeInterval(at("09:00"), at("12:00"))),
                List.of(new BusyInterval(at("10:00"), at("11:00")), new BusyInterval(at("11:00"), at("12:00"))));
        assertEquals(List.of(new FreeInterval(at("09:00"), at("10:00"))), free);
    }
}
