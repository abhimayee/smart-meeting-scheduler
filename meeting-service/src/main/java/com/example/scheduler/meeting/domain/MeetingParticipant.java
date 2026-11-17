package com.example.scheduler.meeting.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.ParticipantType;
import java.util.UUID;

public record MeetingParticipant(UUID id, UUID meetingId, EmailAddress email, ParticipantType type, ResponseStatus responseStatus) {
    public MeetingParticipant(EmailAddress email, ParticipantType type, ResponseStatus responseStatus) {
        this(null, null, email, type, responseStatus);
    }

    public MeetingParticipant {
        if (email == null || type == null) throw new IllegalArgumentException("Participant email and type are required");
        responseStatus = responseStatus == null ? ResponseStatus.PENDING : responseStatus;
    }
}
