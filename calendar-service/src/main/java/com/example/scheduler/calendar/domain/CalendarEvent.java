package com.example.scheduler.calendar.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import java.time.Instant;
import java.util.UUID;

public record CalendarEvent(UUID id, EmailAddress user, String title, Instant startAt, Instant endAt,
                            CalendarEventStatus status, Instant createdAt, Instant updatedAt) {
    public CalendarEvent {
        if (id == null || user == null || startAt == null || endAt == null || status == null)
            throw new IllegalArgumentException("Event id, user, start, end, and status are required");
        if (!startAt.isBefore(endAt)) throw new IllegalArgumentException("Event start must be before end");
        if (title != null) title = title.trim();
    }
}
