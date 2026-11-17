package com.example.scheduler.meeting.domain;

import java.time.Instant;
import com.example.scheduler.shared.domain.EmailAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Meeting aggregate with explicit request, confirmation, and cancellation state transitions. */
public record Meeting(UUID id, String title, String agenda, int durationMinutes, String organizerEmail,
                      MeetingStatus status, Instant createdAt, Instant updatedAt,
                      Instant confirmedStart, Instant confirmedEnd, List<MeetingParticipant> participants) {
    public Meeting {
        if (id == null || status == null) throw new IllegalArgumentException("Meeting id and status are required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Meeting title is required");
        if (durationMinutes <= 0) throw new IllegalArgumentException("Meeting duration must be positive");
        organizerEmail = new EmailAddress(organizerEmail).value();
        if ((confirmedStart == null) != (confirmedEnd == null)) throw new IllegalArgumentException("Confirmed start and end must be provided together");
        if (confirmedStart != null && !confirmedStart.isBefore(confirmedEnd)) throw new IllegalArgumentException("Confirmed start must be before end");
        participants = participants == null ? List.of() : List.copyOf(participants);
    }

    public Meeting confirm(Instant start, Instant end) {
        if (status != MeetingStatus.REQUESTED) throw new IllegalArgumentException("Only requested meetings can be confirmed");
        if (start == null || end == null || !start.isBefore(end)) throw new IllegalArgumentException("Confirmation start must be before end");
        if (!Duration.ofMinutes(durationMinutes).equals(Duration.between(start, end)))
            throw new IllegalArgumentException("Confirmation duration must exactly match the meeting duration");
        return new Meeting(id, title, agenda, durationMinutes, organizerEmail, MeetingStatus.CONFIRMED,
                createdAt, Instant.now(), start, end, participants);
    }

    public Meeting cancel() {
        if (status == MeetingStatus.CANCELLED) return this;
        return new Meeting(id, title, agenda, durationMinutes, organizerEmail, MeetingStatus.CANCELLED,
                createdAt, Instant.now(), confirmedStart, confirmedEnd, participants);
    }
}
