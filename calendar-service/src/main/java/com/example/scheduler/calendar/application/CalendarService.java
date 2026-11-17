package com.example.scheduler.calendar.application;

import com.example.scheduler.calendar.api.CalendarDtos;
import com.example.scheduler.calendar.domain.CalendarEvent;
import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventEntity;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventRepository;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {
    private final CalendarEventRepository eventRepository;
    private final UserProfileClient userProfileClient;
    private final CalendarEventMapper mapper;

    public CalendarService(CalendarEventRepository eventRepository, UserProfileClient userProfileClient, CalendarEventMapper mapper) {
        this.eventRepository = eventRepository;
        this.userProfileClient = userProfileClient;
        this.mapper = mapper;
    }

    @Transactional
    public CalendarDtos.CalendarEventResponse create(CalendarDtos.CreateCalendarEventRequest request) {
        var event = mapper.toDomain(request, UUID.randomUUID());
        userProfileClient.get(event.user().value());
        var entity = new CalendarEventEntity(event.id(), event.user().value(), event.title(), event.startAt(), event.endAt());
        return mapper.toResponse(mapper.toDomain(eventRepository.saveAndFlush(entity)));
    }

    @Transactional(readOnly = true)
    public CalendarDtos.CalendarEventResponse get(UUID eventId) {
        return eventRepository.findById(eventId).map(mapper::toDomain).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar event was not found"));
    }

    @Transactional(readOnly = true)
    public List<CalendarDtos.CalendarEventResponse> getByUser(String email, Instant start, Instant end) {
        var normalized = new EmailAddress(email).value();
        userProfileClient.get(normalized);
        validateRange(start, end);
        List<CalendarEventEntity> events = start == null
                ? eventRepository.findByUserEmailOrderByStartAtAsc(normalized)
                : eventRepository.findByUserEmailAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(normalized, end, start);
        return events.stream().map(mapper::toDomain).map(mapper::toResponse).toList();
    }

    @Transactional
    public void cancel(UUID eventId) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar event was not found"));
        event.cancel();
    }

    private void validateRange(Instant start, Instant end) {
        if ((start == null) != (end == null)) throw new IllegalArgumentException("Both start and end are required for a date-range query");
        if (start != null && !start.isBefore(end)) throw new IllegalArgumentException("Range start must be before end");
    }
}
