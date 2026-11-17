package com.example.scheduler.calendar.api;

import com.example.scheduler.calendar.application.CalendarService;
import com.example.scheduler.calendar.application.UserAvailabilityService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService calendarService;
    private final UserAvailabilityService availabilityService;

    public CalendarController(CalendarService calendarService, UserAvailabilityService availabilityService) {
        this.calendarService = calendarService;
        this.availabilityService = availabilityService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarDtos.CalendarEventResponse create(@Valid @RequestBody CalendarDtos.CreateCalendarEventRequest request) {
        return calendarService.create(request);
    }

    @GetMapping("/events/{eventId}")
    public CalendarDtos.CalendarEventResponse get(@PathVariable UUID eventId) { return calendarService.get(eventId); }

    @GetMapping("/users/{email}/events")
    public List<CalendarDtos.CalendarEventResponse> getByUser(@PathVariable String email,
            @RequestParam(required = false) Instant start, @RequestParam(required = false) Instant end) {
        return calendarService.getByUser(email, start, end);
    }

    @DeleteMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID eventId) { calendarService.cancel(eventId); }

    @GetMapping("/users/{email}/availability")
    public CalendarDtos.AvailabilityResponse availability(@PathVariable String email,
            @RequestParam Instant start, @RequestParam Instant end) {
        var window = availabilityService.getAvailability(email, start, end);
        var free = window.freeIntervals().stream()
                .map(interval -> new CalendarDtos.FreeIntervalResponse(interval.start(), interval.end())).toList();
        return new CalendarDtos.AvailabilityResponse(window.user().value(), window.timeZone().getId(),
                window.rangeStart(), window.rangeEnd(), free);
    }

    @GetMapping("/users/{email}/availability/dates")
    public CalendarDtos.AvailabilityResponse availabilityForDates(@PathVariable String email,
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        var window = availabilityService.getAvailabilityForDates(email, startDate, endDate);
        var free = window.freeIntervals().stream()
                .map(interval -> new CalendarDtos.FreeIntervalResponse(interval.start(), interval.end())).toList();
        return new CalendarDtos.AvailabilityResponse(window.user().value(), window.timeZone().getId(),
                window.rangeStart(), window.rangeEnd(), free);
    }
}
