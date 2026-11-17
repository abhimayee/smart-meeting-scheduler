package com.example.scheduler.calendar.application;

import com.example.scheduler.calendar.api.CalendarDtos;
import com.example.scheduler.calendar.domain.AvailabilityWindow;
import com.example.scheduler.calendar.domain.BusyInterval;
import com.example.scheduler.shared.domain.FreeInterval;
import com.example.scheduler.calendar.domain.IntervalOperations;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventRepository;
import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAvailabilityService {
    private final UserProfileClient userProfileClient;
    private final CalendarEventRepository eventRepository;
    private final IntervalOperations intervalOperations;

    public UserAvailabilityService(UserProfileClient userProfileClient, CalendarEventRepository eventRepository,
                                   IntervalOperations intervalOperations) {
        this.userProfileClient = userProfileClient;
        this.eventRepository = eventRepository;
        this.intervalOperations = intervalOperations;
    }

    @Transactional(readOnly = true)
    public AvailabilityWindow getAvailability(String email, Instant rangeStart, Instant rangeEnd) {
        if (rangeStart == null || rangeEnd == null || !rangeStart.isBefore(rangeEnd))
            throw new IllegalArgumentException("Availability start and end are required and start must be before end");
        var normalizedEmail = new EmailAddress(email).value();
        var user = userProfileClient.get(normalizedEmail);
        ZoneId zone = ZoneId.of(user.timeZone());
        return calculateAvailability(user, normalizedEmail, zone, rangeStart, rangeEnd);
    }

    /** Uses the user's own local calendar dates and treats endDate as inclusive. */
    @Transactional(readOnly = true)
    public AvailabilityWindow getAvailabilityForDates(String email, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate))
            throw new IllegalArgumentException("Availability dates are required and endDate must not precede startDate");
        var normalizedEmail = new EmailAddress(email).value();
        var user = userProfileClient.get(normalizedEmail);
        ZoneId zone = ZoneId.of(user.timeZone());
        Instant rangeStart;
        Instant rangeEnd;
        try {
            rangeStart = startDate.atStartOfDay(zone).toInstant();
            rangeEnd = endDate.plusDays(1).atStartOfDay(zone).toInstant();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Availability date range is outside the supported calendar range", exception);
        }
        if (!rangeStart.isBefore(rangeEnd))
            throw new IllegalArgumentException("Availability dates do not form a positive instant range in the user's time zone");
        return calculateAvailability(user, normalizedEmail, zone, rangeStart, rangeEnd);
    }

    private AvailabilityWindow calculateAvailability(UserProfileClient.UserProfileResponse user,
            String normalizedEmail, ZoneId zone, Instant rangeStart, Instant rangeEnd) {
        var workingWindows = workingWindows(user.workingHours(), zone, rangeStart, rangeEnd);
        var events = eventRepository.findByUserEmailAndStatusAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                normalizedEmail, CalendarEventStatus.BUSY, rangeEnd, rangeStart);
        var busy = events.stream().map(event -> new BusyInterval(
                max(event.getStartAt(), rangeStart), min(event.getEndAt(), rangeEnd))).toList();
        var free = intervalOperations.subtractBusyIntervals(workingWindows, busy);
        return new AvailabilityWindow(new EmailAddress(normalizedEmail), zone, rangeStart, rangeEnd, free);
    }

    private List<FreeInterval> workingWindows(List<UserProfileClient.WorkingHoursResponse> hours,
                                               ZoneId zone, Instant rangeStart, Instant rangeEnd) {
        LocalDate firstDate = rangeStart.atZone(zone).toLocalDate();
        LocalDate lastDate = rangeEnd.minusNanos(1).atZone(zone).toLocalDate();
        var windows = new ArrayList<FreeInterval>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            for (var dailyHours : hours) {
                if (dailyHours.dayOfWeek() != date.getDayOfWeek()) continue;
                Instant localStart = date.atTime(dailyHours.startTime()).atZone(zone).toInstant();
                Instant localEnd = date.atTime(dailyHours.endTime()).atZone(zone).toInstant();
                Instant clippedStart = max(localStart, rangeStart);
                Instant clippedEnd = min(localEnd, rangeEnd);
                if (clippedStart.isBefore(clippedEnd)) windows.add(new FreeInterval(clippedStart, clippedEnd));
            }
        }
        return windows;
    }

    private Instant max(Instant left, Instant right) { return left.isAfter(right) ? left : right; }
    private Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
}
