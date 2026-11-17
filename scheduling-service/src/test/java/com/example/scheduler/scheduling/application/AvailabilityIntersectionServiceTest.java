package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.domain.FreeInterval;
import com.example.scheduler.shared.domain.InstantRange;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AvailabilityIntersectionServiceTest {
    private final DefaultAvailabilityIntersectionService service = new DefaultAvailabilityIntersectionService();
    private Instant at(String time) { return Instant.parse("2026-10-05T" + time + ":00Z"); }

    @Test void intersectsSortedIntervalsAcrossRequiredParticipants() {
        var result = service.intersect(List.of(
                List.of(new FreeInterval(at("09:00"), at("12:00")), new FreeInterval(at("13:00"), at("17:00"))),
                List.of(new FreeInterval(at("10:00"), at("14:00")), new FreeInterval(at("15:00"), at("18:00")))));
        assertEquals(List.of(new InstantRange(at("10:00"), at("12:00")),
                new InstantRange(at("13:00"), at("14:00")),
                new InstantRange(at("15:00"), at("17:00"))), result);
    }

    @Test void intersectsAllParticipantsAndDoesNotLeakSingleParticipantIntervals() {
        var result = service.intersect(List.of(
                List.of(new FreeInterval(at("09:00"), at("12:00")), new FreeInterval(at("13:00"), at("17:00"))),
                List.of(new FreeInterval(at("10:00"), at("14:00")), new FreeInterval(at("15:00"), at("18:00"))),
                List.of(new FreeInterval(at("10:00"), at("11:00")), new FreeInterval(at("13:00"), at("16:00")))));
        assertEquals(List.of(new InstantRange(at("10:00"), at("11:00")),
                new InstantRange(at("13:00"), at("14:00")),
                new InstantRange(at("15:00"), at("16:00"))), result);
    }

    @Test void normalizesUnsortedOverlappingAndAdjacentIntervals() {
        var result = service.normalize(List.of(new FreeInterval(at("10:00"), at("12:00")),
                new FreeInterval(at("09:00"), at("10:00")), new FreeInterval(at("11:00"), at("13:00"))));
        assertEquals(List.of(new InstantRange(at("09:00"), at("13:00"))), result);
    }

    @Test void returnsNoCommonAvailabilityAndRejectsMissingRequiredSet() {
        assertEquals(List.of(), service.intersect(List.of(List.of(new FreeInterval(at("09:00"), at("10:00"))),
                List.of(new FreeInterval(at("10:00"), at("11:00"))))));
        assertThrows(IllegalArgumentException.class, () -> service.intersect(List.of()));
    }

    @Test void adjacentHalfOpenIntervalsHaveNoPositiveDurationIntersection() {
        assertEquals(List.of(), service.intersect(List.of(
                List.of(new FreeInterval(at("09:00"), at("10:00"))),
                List.of(new FreeInterval(at("10:00"), at("11:00"))))));
    }
}
