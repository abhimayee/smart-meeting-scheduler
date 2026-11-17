package com.example.scheduler.meeting.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.ParticipantType;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MeetingRequestTest {
    private final EmailAddress organizer = new EmailAddress("organizer@example.com");
    @Test void rejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> new MeetingRequest("Review", null, null, Duration.ZERO, organizer, null, null, null, null, null));
    }
    @Test void rejectsDuplicateParticipantsAfterEmailNormalization() {
        var participants = List.of(
                new MeetingParticipant(new EmailAddress("JOHN@example.com"), ParticipantType.REQUIRED, null),
                new MeetingParticipant(new EmailAddress("john@example.com"), ParticipantType.OPTIONAL, null));
        assertThrows(IllegalArgumentException.class, () -> new MeetingRequest("Review", null, null, Duration.ofMinutes(30), organizer, participants, null, null, null, null));
    }
    @Test void requiresPreferredDatesInOrder() {
        assertThrows(IllegalArgumentException.class, () -> new MeetingRequest("Review", null, null, Duration.ofMinutes(30), organizer, null,
                java.time.LocalDate.parse("2026-10-10"), java.time.LocalDate.parse("2026-10-09"), null, null));
    }
}
