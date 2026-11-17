package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.scheduling.domain.ParticipantAvailability;
import com.example.scheduler.scheduling.domain.SlotSuggestion;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.InstantRange;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotRankingServiceTest {
    private SlotSuggestion slot(String start, int optionalCount) {
        var from = Instant.parse("2026-10-05T" + start + ":00Z");
        var organizer = new EmailAddress("org@example.com");
        var optional = new EmailAddress("opt@example.com");
        var attendees = List.of(
                new ParticipantAvailability(organizer, ParticipantType.REQUIRED, true, from.atZone(ZoneId.of("UTC")), from.plusSeconds(3600).atZone(ZoneId.of("UTC"))),
                new ParticipantAvailability(optional, ParticipantType.OPTIONAL, optionalCount > 0,
                        from.atZone(ZoneId.of("UTC")), from.plusSeconds(3600).atZone(ZoneId.of("UTC"))));
        return new SlotSuggestion(new InstantRange(from, from.plusSeconds(3600)), true, optionalCount, 1, attendees);
    }

    @Test void ranksMoreOptionalAttendeesAheadOfPreferencesAndEarlierTime() {
        var ranked = new SlotRankingService().rank(List.of(slot("11:00", 0), slot("14:00", 1)),
                new EmailAddress("org@example.com"), ZoneId.of("UTC"), null, null);
        assertEquals(1, ranked.getFirst().optionalParticipantsAvailable());
    }

    @Test void ranksPreferredDateAndTimeBeforeLocalMiddayAndEarliestTieBreak() {
        var ranked = new SlotRankingService().rank(List.of(slot("14:00", 0), slot("10:00", 0), slot("11:00", 0)),
                new EmailAddress("org@example.com"), ZoneId.of("UTC"), java.time.LocalDate.parse("2026-10-05"), java.time.LocalTime.NOON);
        assertEquals(0, ranked.getFirst().interval().start().compareTo(Instant.parse("2026-10-05T11:00:00Z")));
        assertEquals(Instant.parse("2026-10-05T10:00:00Z"), ranked.get(1).interval().start());
    }

    @Test void breaksEquivalentRankingByEarlierInstantDeterministically() {
        // These starts are equally far from local noon, so only the final UTC tie-breaker differs.
        var ranked = new SlotRankingService().rank(List.of(slot("12:15", 0), slot("11:45", 0)),
                new EmailAddress("org@example.com"), ZoneId.of("UTC"), null, null);
        assertEquals(Instant.parse("2026-10-05T11:45:00Z"), ranked.getFirst().interval().start());
    }
}
