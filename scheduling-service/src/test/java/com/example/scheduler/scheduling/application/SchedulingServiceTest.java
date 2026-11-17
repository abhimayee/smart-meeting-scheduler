package com.example.scheduler.scheduling.application;

import com.example.scheduler.scheduling.domain.CalendarAvailabilitySnapshot;
import com.example.scheduler.shared.domain.FreeInterval;
import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.scheduling.api.SchedulingDtos;
import com.example.scheduler.shared.domain.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {
    @Mock CalendarAvailabilityClient availabilityService;
    private SchedulingService service;

    private static final String ORGANIZER = "abhipsa@example.com";
    private static final LocalDate MONDAY = LocalDate.parse("2026-10-05");
    private static final Instant RANGE_START = Instant.parse("2026-10-05T00:00:00Z");
    private static final Instant RANGE_END = Instant.parse("2026-10-06T00:00:00Z");

    @BeforeEach void setUp() {
        service = new SchedulingService(availabilityService, new DefaultAvailabilityIntersectionService(),
                new SlotGenerationService(15), new SlotRankingService());
    }

    private CalendarAvailabilitySnapshot window(String email, String zone, String... startEndPairs) {
        var intervals = new java.util.ArrayList<FreeInterval>();
        for (int index = 0; index < startEndPairs.length; index += 2)
            intervals.add(new FreeInterval(Instant.parse(startEndPairs[index]), Instant.parse(startEndPairs[index + 1])));
        return new CalendarAvailabilitySnapshot(new EmailAddress(email), ZoneId.of(zone), RANGE_START, RANGE_END, intervals);
    }

    private void stub(LocalDate date, CalendarAvailabilitySnapshot organizerWindow, CalendarAvailabilitySnapshot... otherWindows) {
        when(availabilityService.forDateRange(ORGANIZER, date, date)).thenReturn(organizerWindow);
        for (var other : otherWindows)
            when(availabilityService.forInstantRange(other.email().value(), RANGE_START, RANGE_END)).thenReturn(other);
    }

    private SchedulingDtos.FindSlotsRequest request(List<SchedulingDtos.ParticipantRequest> participants,
                                                     int duration, LocalDate start, LocalDate end,
                                                     LocalDate preferredDate, LocalTime preferredTime) {
        return new SchedulingDtos.FindSlotsRequest(ORGANIZER, participants, duration, start, end, preferredDate, preferredTime);
    }

    private SchedulingDtos.ParticipantRequest participant(String email, ParticipantType type) {
        return new SchedulingDtos.ParticipantRequest(email, type);
    }

    private CalendarAvailabilitySnapshot utcWorkday(String email, String... intervals) {
        return window(email, "UTC", intervals);
    }

    @Test void returnsSlotsWhenAllRequiredParticipantsAreFreeAndIncludesOrganizer() {
        var john = utcWorkday("john@example.com", "2026-10-05T09:00:00Z", "2026-10-05T11:00:00Z");
        stub(MONDAY, utcWorkday(ORGANIZER, "2026-10-05T09:00:00Z", "2026-10-05T11:00:00Z"), john);
        var results = service.findSlots(request(List.of(participant("john@example.com", ParticipantType.REQUIRED)),
                60, MONDAY, MONDAY, null, null));
        assertFalse(results.isEmpty());
        assertTrue(results.getFirst().requiredParticipantsAvailable());
        assertEquals(ORGANIZER, results.getFirst().participants().getFirst().email().value());
        assertEquals(ParticipantType.REQUIRED, results.getFirst().participants().getFirst().type());
    }

    @Test void unavailableRequiredParticipantMakesItsSlotInvalid() {
        var john = utcWorkday("john@example.com", "2026-10-05T10:00:00Z", "2026-10-11T00:00:00Z");
        stub(MONDAY, utcWorkday(ORGANIZER, "2026-10-05T09:00:00Z", "2026-10-05T12:00:00Z"), john);
        var results = service.findSlots(request(List.of(participant("john@example.com", ParticipantType.REQUIRED)),
                60, MONDAY, MONDAY, null, null));
        assertTrue(results.stream().noneMatch(slot -> slot.interval().start().equals(Instant.parse("2026-10-05T09:00:00Z"))));
        assertTrue(results.stream().allMatch(slot -> slot.requiredParticipantsAvailable()));
    }

    @Test void unavailableOptionalParticipantDoesNotBlockSlots() {
        var optional = utcWorkday("sarah@example.com");
        stub(MONDAY, utcWorkday(ORGANIZER, "2026-10-05T09:00:00Z", "2026-10-05T11:00:00Z"), optional);
        var results = service.findSlots(request(List.of(participant("sarah@example.com", ParticipantType.OPTIONAL)),
                60, MONDAY, MONDAY, null, null));
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(slot -> slot.optionalParticipantsAvailable() == 0));
        assertFalse(results.getFirst().participants().get(1).available());
    }

    @Test void optionalAvailabilityRanksAheadOfOtherwiseEquivalentSlot() {
        var optional = utcWorkday("sarah@example.com", "2026-10-05T10:00:00Z", "2026-10-05T12:00:00Z");
        stub(MONDAY, utcWorkday(ORGANIZER, "2026-10-05T09:00:00Z", "2026-10-05T12:00:00Z"), optional);
        var results = service.findSlots(request(List.of(participant("sarah@example.com", ParticipantType.OPTIONAL)),
                60, MONDAY, MONDAY, null, null));
        assertEquals(1, results.getFirst().optionalParticipantsAvailable());
        assertTrue(results.getFirst().interval().start().compareTo(Instant.parse("2026-10-05T09:00:00Z")) > 0);
    }

    @Test void countsMultipleOptionalAttendeesWhenRanking() {
        var sarah = utcWorkday("sarah@example.com", "2026-10-05T10:00:00Z", "2026-10-05T12:00:00Z");
        var david = utcWorkday("david@example.com", "2026-10-05T11:00:00Z", "2026-10-05T12:00:00Z");
        stub(MONDAY, utcWorkday(ORGANIZER, "2026-10-05T09:00:00Z", "2026-10-05T12:00:00Z"), sarah, david);
        var results = service.findSlots(request(List.of(participant("sarah@example.com", ParticipantType.OPTIONAL),
                        participant("david@example.com", ParticipantType.OPTIONAL)), 60, MONDAY, MONDAY, null, null));
        assertEquals(2, results.getFirst().optionalParticipantsAvailable());
        assertEquals(2, results.getFirst().optionalParticipantsTotal());
        assertTrue(results.getFirst().interval().start().compareTo(Instant.parse("2026-10-05T11:00:00Z")) >= 0);
    }

    @Test void mapsSameUtcSlotToEachParticipantsLocalZone() {
        var start = "2026-10-05T13:30:00Z";
        var end = "2026-10-05T14:30:00Z";
        var john = window("john@example.com", "America/New_York", start, end);
        var sarah = window("sarah@example.com", "Europe/London", start, end);
        var abhipsa = window(ORGANIZER, "Asia/Kolkata", start, end);
        var localRangeStart = Instant.parse("2026-10-04T18:30:00Z");
        var localRangeEnd = Instant.parse("2026-10-07T18:30:00Z");
        abhipsa = new CalendarAvailabilitySnapshot(abhipsa.email(), abhipsa.timeZone(), localRangeStart, localRangeEnd, abhipsa.freeIntervals());
        when(availabilityService.forDateRange(ORGANIZER, MONDAY, MONDAY)).thenReturn(abhipsa);
        when(availabilityService.forInstantRange("john@example.com", localRangeStart, localRangeEnd)).thenReturn(john);
        when(availabilityService.forInstantRange("sarah@example.com", localRangeStart, localRangeEnd)).thenReturn(sarah);
        var results = service.findSlots(request(List.of(participant("john@example.com", ParticipantType.REQUIRED),
                        participant("sarah@example.com", ParticipantType.OPTIONAL)), 60, MONDAY, MONDAY, null, null));
        var slot = results.getFirst();
        assertEquals(Instant.parse(start), slot.interval().start());
        assertEquals("2026-10-05T19:00+05:30[Asia/Kolkata]", slot.participants().get(0).localStart().toString());
        assertEquals("2026-10-05T09:30-04:00[America/New_York]", slot.participants().get(1).localStart().toString());
        assertEquals("2026-10-05T14:30+01:00[Europe/London]", slot.participants().get(2).localStart().toString());
    }

    @Test void handlesNewYorkDstChangeUsingZoneRules() {
        var date = LocalDate.parse("2026-03-08");
        var start = Instant.parse("2026-03-08T07:00:00Z");
        var end = Instant.parse("2026-03-08T08:00:00Z");
        var org = new CalendarAvailabilitySnapshot(new EmailAddress(ORGANIZER), ZoneId.of("America/New_York"),
                Instant.parse("2026-03-08T05:00:00Z"), Instant.parse("2026-03-09T04:00:00Z"), List.of(new FreeInterval(start, end)));
        var john = window("john@example.com", "Europe/London", start.toString(), end.toString());
        when(availabilityService.forDateRange(ORGANIZER, date, date)).thenReturn(org);
        when(availabilityService.forInstantRange("john@example.com", org.rangeStart(), org.rangeEnd())).thenReturn(john);
        var result = service.findSlots(request(List.of(participant("john@example.com", ParticipantType.REQUIRED)), 60, date, date, null, null));
        assertEquals(start, result.getFirst().interval().start());
        assertEquals("2026-03-08T03:00-04:00[America/New_York]", result.getFirst().participants().getFirst().localStart().toString());
    }

    @Test void preferredDateAndStartTimeAffectRanking() {
        var date2 = MONDAY.plusDays(1);
        var dayOneStart = Instant.parse("2026-10-05T09:00:00Z");
        var dayOneEnd = Instant.parse("2026-10-05T12:00:00Z");
        var dayTwoStart = Instant.parse("2026-10-06T09:00:00Z");
        var dayTwoEnd = Instant.parse("2026-10-06T12:00:00Z");
        var org = window(ORGANIZER, "UTC", dayOneStart.toString(), dayOneEnd.toString(), dayTwoStart.toString(), dayTwoEnd.toString());
        org = new CalendarAvailabilitySnapshot(org.email(), org.timeZone(), Instant.parse("2026-10-05T00:00:00Z"),
                Instant.parse("2026-10-07T00:00:00Z"), org.freeIntervals());
        when(availabilityService.forDateRange(ORGANIZER, MONDAY, date2)).thenReturn(org);
        var ranked = service.findSlots(request(List.of(), 60, MONDAY, date2, date2, LocalTime.of(10, 0)));
        assertEquals(Instant.parse("2026-10-06T10:00:00Z"), ranked.getFirst().interval().start());
    }

    @Test void preferredLocalTimeIsRankedInOrganizerTimeZoneNotUtc() {
        var organizer = new CalendarAvailabilitySnapshot(new EmailAddress(ORGANIZER), ZoneId.of("America/New_York"),
                Instant.parse("2026-10-05T04:00:00Z"), Instant.parse("2026-10-06T04:00:00Z"), List.of(
                new FreeInterval(Instant.parse("2026-10-05T13:00:00Z"), Instant.parse("2026-10-05T13:30:00Z")),
                new FreeInterval(Instant.parse("2026-10-05T14:00:00Z"), Instant.parse("2026-10-05T14:30:00Z"))));
        when(availabilityService.forDateRange(ORGANIZER, MONDAY, MONDAY)).thenReturn(organizer);

        var ranked = service.findSlots(request(List.of(), 30, MONDAY, MONDAY, MONDAY, LocalTime.of(10, 0)));

        assertEquals(Instant.parse("2026-10-05T14:00:00Z"), ranked.getFirst().interval().start());
        assertEquals("2026-10-05T10:00-04:00[America/New_York]",
                ranked.getFirst().participants().getFirst().localStart().toString());
    }

    @Test void explicitPreferenceZoneControlsInclusiveDateBoundariesAndLocalTimeRanking() {
        var date = LocalDate.parse("2026-10-05");
        var start = Instant.parse("2026-10-05T07:00:00Z");
        var end = Instant.parse("2026-10-06T07:00:00Z");
        var organizer = new CalendarAvailabilitySnapshot(new EmailAddress(ORGANIZER), ZoneId.of("Asia/Kolkata"),
                start, end, List.of(new FreeInterval(Instant.parse("2026-10-05T16:00:00Z"),
                Instant.parse("2026-10-05T17:00:00Z"))));
        when(availabilityService.forInstantRange(ORGANIZER, start, end)).thenReturn(organizer);

        var request = new SchedulingDtos.FindSlotsRequest(ORGANIZER, List.of(), 30, date, date, date,
                LocalTime.of(9, 0), "America/Los_Angeles");
        var slots = service.findSlots(request);

        assertFalse(slots.isEmpty());
        assertEquals(Instant.parse("2026-10-05T16:00:00Z"), slots.getFirst().interval().start());
        verify(availabilityService).forInstantRange(ORGANIZER, start, end);
        verify(availabilityService, never()).forDateRange(anyString(), any(), any());
    }

    @Test void explicitNewYorkPreferenceZoneUsesDstAwareDateBoundariesAndRanking() {
        var date = LocalDate.parse("2026-03-08");
        var start = Instant.parse("2026-03-08T05:00:00Z");
        var end = Instant.parse("2026-03-09T04:00:00Z");
        var organizer = new CalendarAvailabilitySnapshot(new EmailAddress(ORGANIZER), ZoneId.of("Asia/Kolkata"),
                start, end, List.of(new FreeInterval(Instant.parse("2026-03-08T06:30:00Z"),
                Instant.parse("2026-03-08T07:30:00Z"))));
        when(availabilityService.forInstantRange(ORGANIZER, start, end)).thenReturn(organizer);

        var request = new SchedulingDtos.FindSlotsRequest(ORGANIZER, List.of(), 15, date, date, date,
                LocalTime.of(3, 0), "America/New_York");
        var slots = service.findSlots(request);

        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), slots.getFirst().interval().start());
        assertEquals("2026-03-08T03:00-04:00[America/New_York]",
                slots.getFirst().interval().start().atZone(ZoneId.of("America/New_York")).toString());
        verify(availabilityService).forInstantRange(ORGANIZER, start, end);
    }

    @Test void rejectsInvalidPreferenceZoneWithoutCallingAvailabilityService() {
        var request = new SchedulingDtos.FindSlotsRequest(ORGANIZER, List.of(), 30, MONDAY, MONDAY,
                MONDAY, LocalTime.NOON, "Not/A_Zone");
        assertThrows(IllegalArgumentException.class, () -> service.findSlots(request));
        verifyNoInteractions(availabilityService);
    }

    @Test void rejectsDuplicateParticipantsAndInvalidDateOrDuration() {
        var duplicate = request(List.of(participant("JOHN@example.com", ParticipantType.REQUIRED),
                participant("john@example.com", ParticipantType.OPTIONAL)), 30, MONDAY, MONDAY, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.findSlots(duplicate));
        assertThrows(IllegalArgumentException.class, () -> service.findSlots(request(List.of(), 0, MONDAY, MONDAY, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.findSlots(request(List.of(), 30, MONDAY.plusDays(1), MONDAY, null, null)));
        verifyNoInteractions(availabilityService);
    }
}
