package com.example.scheduler.calendar.application;

import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.calendar.domain.IntervalOperations;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventEntity;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventRepository;
import com.example.scheduler.shared.domain.FreeInterval;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAvailabilityServiceTest {
    @Mock UserProfileClient userProfileClient;
    @Mock CalendarEventRepository eventRepository;

    private UserProfileClient.UserProfileResponse user(String email, String zone, UserProfileClient.WorkingHoursResponse... hours) {
        return new UserProfileClient.UserProfileResponse(UUID.randomUUID(), email, "Test User", zone,
                List.of(hours), Instant.EPOCH, Instant.EPOCH);
    }

    private UserProfileClient.WorkingHoursResponse hours(DayOfWeek day, String start, String end) {
        return new UserProfileClient.WorkingHoursResponse(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    private CalendarEventEntity event(String email, String start, String end) {
        return new CalendarEventEntity(UUID.randomUUID(), email, "Busy", Instant.parse(start), Instant.parse(end));
    }

    private List<FreeInterval> availability(UserProfileClient.UserProfileResponse user, Instant start, Instant end,
                                            CalendarEventEntity... events) {
        when(userProfileClient.get(user.email())).thenReturn(user);
        when(eventRepository.findByUserEmailAndStatusAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                eq(user.email()), eq(CalendarEventStatus.BUSY), eq(end), eq(start))).thenReturn(List.of(events));
        return new UserAvailabilityService(userProfileClient, eventRepository, new IntervalOperations())
                .getAvailability(user.email(), start, end).freeIntervals();
    }

    private Instant utc(String time) { return Instant.parse("2026-10-05T" + time + ":00Z"); }

    @Test void userWithNoEventsIsFreeDuringWorkingHours() {
        var user = user("u@example.com", "UTC", hours(DayOfWeek.MONDAY, "09:00", "17:00"));
        assertEquals(List.of(new FreeInterval(utc("09:00"), utc("17:00"))), availability(user, utc("00:00"), utc("23:59")));
    }

    @Test void subtractsOneAndMultipleBusyEvents() {
        var user = user("u@example.com", "UTC", hours(DayOfWeek.MONDAY, "09:00", "17:00"));
        var one = availability(user, utc("00:00"), utc("23:59"), event(user.email(), "2026-10-05T11:00:00Z", "2026-10-05T12:00:00Z"));
        assertEquals(List.of(new FreeInterval(utc("09:00"), utc("11:00")), new FreeInterval(utc("12:00"), utc("17:00"))), one);
        reset(userProfileClient, eventRepository);
        var multiple = availability(user, utc("00:00"), utc("23:59"),
                event(user.email(), "2026-10-05T10:00:00Z", "2026-10-05T11:00:00Z"),
                event(user.email(), "2026-10-05T14:00:00Z", "2026-10-05T15:30:00Z"));
        assertEquals(List.of(new FreeInterval(utc("09:00"), utc("10:00")), new FreeInterval(utc("11:00"), utc("14:00")),
                new FreeInterval(utc("15:30"), utc("17:00"))), multiple);
    }

    @Test void overlappingAndAdjacentBusyEventsDoNotCreateFalseAvailability() {
        var user = user("u@example.com", "UTC", hours(DayOfWeek.MONDAY, "09:00", "17:00"));
        var result = availability(user, utc("00:00"), utc("23:59"), event(user.email(), "2026-10-05T10:00:00Z", "2026-10-05T11:00:00Z"),
                event(user.email(), "2026-10-05T10:30:00Z", "2026-10-05T12:00:00Z"), event(user.email(), "2026-10-05T12:00:00Z", "2026-10-05T13:00:00Z"));
        assertEquals(List.of(new FreeInterval(utc("09:00"), utc("10:00")), new FreeInterval(utc("13:00"), utc("17:00"))), result);
    }

    @Test void clipsToWorkingHoursAndRequestedRange() {
        var user = user("u@example.com", "UTC", hours(DayOfWeek.MONDAY, "09:00", "17:00"));
        var outside = availability(user, utc("00:00"), utc("23:59"), event(user.email(), "2026-10-05T07:00:00Z", "2026-10-05T08:00:00Z"));
        assertEquals(List.of(new FreeInterval(utc("09:00"), utc("17:00"))), outside);
        reset(userProfileClient, eventRepository);
        var partial = availability(user, utc("10:00"), utc("12:00"), event(user.email(), "2026-10-05T09:30:00Z", "2026-10-05T10:30:00Z"));
        assertEquals(List.of(new FreeInterval(utc("10:30"), utc("12:00"))), partial);
    }

    @Test void cancelledEventsAreNotIncludedInBusyQuery() {
        var user = user("u@example.com", "UTC", hours(DayOfWeek.MONDAY, "09:00", "17:00"));
        assertEquals(List.of(new FreeInterval(utc("09:00"), utc("17:00"))), availability(user, utc("00:00"), utc("23:59")));
        verify(eventRepository).findByUserEmailAndStatusAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                user.email(), CalendarEventStatus.BUSY, utc("23:59"), utc("00:00"));
    }

    @Test void supportsMultipleWorkingIntervalsAndNoHours() {
        var user = user("u@example.com", "UTC", hours(DayOfWeek.MONDAY, "09:00", "12:00"), hours(DayOfWeek.MONDAY, "13:00", "17:00"));
        var free = availability(user, utc("00:00"), utc("23:59"), event(user.email(), "2026-10-05T10:00:00Z", "2026-10-05T11:00:00Z"));
        assertEquals(List.of(new FreeInterval(utc("09:00"), utc("10:00")), new FreeInterval(utc("11:00"), utc("12:00")),
                new FreeInterval(utc("13:00"), utc("17:00"))), free);
        reset(userProfileClient, eventRepository);
        var noHours = user("empty@example.com", "UTC");
        assertEquals(List.of(), availability(noHours, utc("00:00"), utc("23:59")));
    }

    @ParameterizedTest @CsvSource({"Asia/Kolkata,2026-01-05T03:30:00Z", "America/New_York,2026-01-05T14:00:00Z",
            "Europe/London,2026-01-05T09:00:00Z", "Asia/Singapore,2026-01-05T01:00:00Z"})
    void convertsLocalHoursAcrossIanaZones(String zone, String expectedStart) {
        var date = LocalDate.parse("2026-01-05");
        var user = user("u@example.com", zone, hours(DayOfWeek.MONDAY, "09:00", "10:00"));
        var start = date.atStartOfDay(ZoneId.of(zone)).toInstant();
        var end = date.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant();
        var free = availability(user, start, end);
        assertEquals(Instant.parse(expectedStart), free.getFirst().start());
        assertEquals(Instant.parse(expectedStart).plusSeconds(3600), free.getFirst().end());
    }

    @Test void observesNewYorkDaylightSavingTransition() {
        var user = user("u@example.com", "America/New_York", hours(DayOfWeek.SUNDAY, "01:00", "04:00"));
        var free = availability(user, Instant.parse("2026-03-08T00:00:00Z"), Instant.parse("2026-03-09T00:00:00Z"));
        assertEquals(List.of(new FreeInterval(Instant.parse("2026-03-08T06:00:00Z"), Instant.parse("2026-03-08T08:00:00Z"))), free);
    }

    @Test void dateBasedAvailabilityUsesInclusiveDatesInTheUsersZone() {
        var user = user("u@example.com", "Asia/Kolkata", hours(DayOfWeek.MONDAY, "09:00", "17:00"));
        when(userProfileClient.get(user.email())).thenReturn(user);
        when(eventRepository.findByUserEmailAndStatusAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
                eq(user.email()), eq(CalendarEventStatus.BUSY), any(), any())).thenReturn(List.of());
        var result = new UserAvailabilityService(userProfileClient, eventRepository, new IntervalOperations())
                .getAvailabilityForDates(user.email(), LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-05"));
        assertEquals(Instant.parse("2026-10-04T18:30:00Z"), result.rangeStart());
        assertEquals(Instant.parse("2026-10-05T18:30:00Z"), result.rangeEnd());
    }
}
