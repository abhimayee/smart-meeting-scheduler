package com.example.scheduler.calendar.application;

import com.example.scheduler.calendar.api.CalendarDtos;
import com.example.scheduler.calendar.domain.CalendarEvent;
import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventEntity;
import com.example.scheduler.shared.domain.EmailAddress;
import org.springframework.stereotype.Component;

@Component
public class CalendarEventMapper {
    public CalendarEvent toDomain(CalendarEventEntity entity) {
        return new CalendarEvent(entity.getId(), new EmailAddress(entity.getUserEmail()), entity.getTitle(),
                entity.getStartAt(), entity.getEndAt(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public CalendarDtos.CalendarEventResponse toResponse(CalendarEvent event) {
        return new CalendarDtos.CalendarEventResponse(event.id(), event.user().value(), event.title(), event.startAt(),
                event.endAt(), event.status(), event.createdAt(), event.updatedAt());
    }

    public CalendarEvent toDomain(CalendarDtos.CreateCalendarEventRequest request, java.util.UUID id) {
        return new CalendarEvent(id, new EmailAddress(request.userEmail()), request.title(), request.startAt(),
                request.endAt(), CalendarEventStatus.BUSY, null, null);
    }
}
