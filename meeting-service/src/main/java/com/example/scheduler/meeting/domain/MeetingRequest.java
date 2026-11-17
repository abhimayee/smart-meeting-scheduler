package com.example.scheduler.meeting.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

public record MeetingRequest(String title, String description, List<String> agenda, Duration duration,
                             EmailAddress organizer, List<MeetingParticipant> participants,
                             LocalDate preferredStartDate, LocalDate preferredEndDate, LocalTime preferredStartTime,
                             MeetingType meetingType) {
    public MeetingRequest {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Meeting title must not be blank");
        title = title.trim();
        if (duration == null || duration.isZero() || duration.isNegative()) throw new IllegalArgumentException("Meeting duration must be positive");
        if (organizer == null) throw new IllegalArgumentException("Organizer is required");
        if (preferredStartDate != null && preferredEndDate != null && preferredEndDate.isBefore(preferredStartDate))
            throw new IllegalArgumentException("Preferred end date must not be before start date");
        agenda = agenda == null ? List.of() : List.copyOf(agenda);
        participants = participants == null ? List.of() : List.copyOf(participants);
        var emails = new HashSet<EmailAddress>();
        emails.add(organizer);
        if (participants.stream().anyMatch(p -> !emails.add(p.email())))
            throw new IllegalArgumentException("Participants must be unique and must not duplicate the organizer");
        meetingType = meetingType == null ? MeetingType.STANDARD : meetingType;
    }
}
