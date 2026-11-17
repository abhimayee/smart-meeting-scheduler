package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.domain.InstantRange;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlotGenerationServiceTest {
    private final SlotGenerationService service = new SlotGenerationService(15);
    private Instant at(String time) { return Instant.parse("2026-10-05T" + time + ":00Z"); }

    @Test void generatesGranularityAlignedSlotsWithoutMinuteScanning() {
        var slots = service.generate(List.of(new InstantRange(at("09:00"), at("12:00"))), Duration.ofHours(1));
        assertEquals(9, slots.size());
        assertEquals(new InstantRange(at("09:00"), at("10:00")), slots.getFirst());
        assertEquals(new InstantRange(at("10:45"), at("11:45")), slots.get(7));
        assertEquals(new InstantRange(at("11:00"), at("12:00")), slots.getLast());
    }

    @Test void acceptsExactDurationAndRejectsTooShortIntervals() {
        assertEquals(List.of(new InstantRange(at("09:00"), at("10:00"))),
                service.generate(List.of(new InstantRange(at("09:00"), at("10:00"))), Duration.ofHours(1)));
        assertEquals(List.of(), service.generate(List.of(new InstantRange(at("09:00"), at("09:59"))), Duration.ofHours(1)));
    }

    @Test void requiresPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(List.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new SlotGenerationService(0));
    }
}
