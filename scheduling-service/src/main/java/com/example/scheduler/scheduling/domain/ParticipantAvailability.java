package com.example.scheduler.scheduling.domain;

import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.shared.domain.EmailAddress;
import java.time.ZonedDateTime;

public record ParticipantAvailability(EmailAddress email, ParticipantType type, boolean available,
                                      ZonedDateTime localStart, ZonedDateTime localEnd) {
    public ParticipantAvailability {
        if (email == null || type == null) throw new IllegalArgumentException("Participant email and type are required");
        if (available && (localStart == null || localEnd == null)) throw new IllegalArgumentException("Available participants require local slot times");
    }
}
