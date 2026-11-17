package com.example.scheduler.calendar.application;

import com.example.scheduler.calendar.api.CalendarDtos;
import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.calendar.domain.CalendarEvent;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventEntity;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventRepository;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {
    @Mock CalendarEventRepository eventRepository;
    @Mock UserProfileClient userProfileClient;
    @Mock CalendarEventMapper mapper;
    @InjectMocks CalendarService service;

    private CalendarDtos.CreateCalendarEventRequest request(String email, Instant start, Instant end) {
        return new CalendarDtos.CreateCalendarEventRequest(email, "Design review", start, end);
    }

    @Test void createsEventForExistingUser() {
        var request = request("A@EXAMPLE.COM", Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T10:00:00Z"));
        var domain = new CalendarEvent(UUID.randomUUID(), new EmailAddress("a@example.com"), "Design review",
                request.startAt(), request.endAt(), CalendarEventStatus.BUSY, null, null);
        when(mapper.toDomain(eq(request), any(UUID.class))).thenReturn(domain);
        when(eventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = new CalendarDtos.CalendarEventResponse(UUID.randomUUID(), "a@example.com", "Design review",
                request.startAt(), request.endAt(), CalendarEventStatus.BUSY, Instant.now(), Instant.now());
        when(mapper.toDomain(any(CalendarEventEntity.class))).thenReturn(domain);
        when(mapper.toResponse(domain)).thenReturn(response);

        assertEquals(response, service.create(request));
        verify(eventRepository).saveAndFlush(any(CalendarEventEntity.class));
    }

    @Test void createRejectsUnknownUser() {
        var request = request("missing@example.com", Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T10:00:00Z"));
        var domain = new CalendarEvent(UUID.randomUUID(), new EmailAddress("missing@example.com"), "Design review",
                request.startAt(), request.endAt(), CalendarEventStatus.BUSY, null, null);
        when(mapper.toDomain(eq(request), any(UUID.class))).thenReturn(domain);
        doThrow(new ResourceNotFoundException("User was not found")).when(userProfileClient).get("missing@example.com");
        assertThrows(ResourceNotFoundException.class, () -> service.create(request));
        verifyNoInteractions(eventRepository);
    }

    @Test void retrievalRejectsUnknownEvent() {
        when(eventRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(UUID.randomUUID()));
    }

    @Test void retrievesExistingEvent() {
        var id = UUID.randomUUID();
        var entity = new CalendarEventEntity(id, "a@example.com", "Review", Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T10:00:00Z"));
        var domain = new com.example.scheduler.calendar.domain.CalendarEvent(id, new com.example.scheduler.shared.domain.EmailAddress("a@example.com"),
                "Review", entity.getStartAt(), entity.getEndAt(), CalendarEventStatus.BUSY, Instant.now(), Instant.now());
        var response = new CalendarDtos.CalendarEventResponse(id, "a@example.com", "Review", entity.getStartAt(), entity.getEndAt(),
                CalendarEventStatus.BUSY, Instant.now(), Instant.now());
        when(eventRepository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);
        when(mapper.toResponse(domain)).thenReturn(response);
        assertEquals(response, service.get(id));
    }

    @Test void rangeQueryUsesOverlapSemanticsAndNormalizesEmail() {
        when(userProfileClient.get("a@example.com")).thenReturn(null);
        when(eventRepository.findByUserEmailAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(anyString(), any(), any())).thenReturn(List.of());
        var start = Instant.parse("2026-10-05T09:00:00Z");
        var end = start.plusSeconds(3600);
        assertEquals(List.of(), service.getByUser("A@example.com", start, end));
        verify(eventRepository).findByUserEmailAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc("a@example.com", end, start);
    }

    @Test void rangeQueryRejectsUnknownUserAndReversedRange() {
        doThrow(new ResourceNotFoundException("User was not found")).when(userProfileClient).get("missing@example.com");
        assertThrows(ResourceNotFoundException.class, () -> service.getByUser("missing@example.com", null, null));
        when(userProfileClient.get("a@example.com")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.getByUser("a@example.com", Instant.MAX, Instant.MIN));
    }

    @Test void deleteCancelsInsteadOfRemovingEvent() {
        var event = new CalendarEventEntity(UUID.randomUUID(), "a@example.com",
                "Review", Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T10:00:00Z"));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        service.cancel(event.getId());
        assertEquals(CalendarEventStatus.CANCELLED, event.getStatus());
        assertFalse(event.isBusy());
    }

    @Test void deleteRejectsUnknownEvent() {
        when(eventRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.cancel(UUID.randomUUID()));
    }

    @Test void domainRejectsNonIncreasingEventRange() {
        var email = new EmailAddress("a@example.com");
        var start = Instant.parse("2026-10-05T09:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new com.example.scheduler.calendar.domain.CalendarEvent(
                UUID.randomUUID(), email, "Review", start, start, CalendarEventStatus.BUSY, null, null));
    }
}
