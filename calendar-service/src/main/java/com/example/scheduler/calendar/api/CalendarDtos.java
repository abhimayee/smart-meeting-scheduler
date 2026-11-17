package com.example.scheduler.calendar.api;

import com.example.scheduler.calendar.domain.CalendarEventStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CalendarDtos {
    private CalendarDtos() { }

    public record CreateCalendarEventRequest(@NotBlank @Email @Size(max = 320) String userEmail,
                                             @Size(max = 300) String title,
                                             @NotNull Instant startAt,
                                             @NotNull Instant endAt) { }

    public record CalendarEventResponse(UUID id, String userEmail, String title, Instant startAt, Instant endAt,
                                        CalendarEventStatus status, Instant createdAt, Instant updatedAt) { }

    public record FreeIntervalResponse(Instant start, Instant end) { }

    public record AvailabilityResponse(String userEmail, String timeZone, Instant rangeStart, Instant rangeEnd,
                                       List<FreeIntervalResponse> freeIntervals) { }
}
