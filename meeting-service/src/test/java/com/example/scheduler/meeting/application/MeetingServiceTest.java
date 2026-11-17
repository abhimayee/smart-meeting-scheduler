package com.example.scheduler.meeting.application;

import com.example.scheduler.meeting.api.MeetingDtos;
import com.example.scheduler.meeting.domain.MeetingStatus;
import com.example.scheduler.meeting.domain.MeetingType;
import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingEntity;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingParticipantEntity;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingParticipantRepository;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingRepository;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ConflictException;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {
    private static final String ORGANIZER = "organizer@example.com";
    @Mock MeetingRepository meetingRepository;
    @Mock MeetingParticipantRepository participantRepository;
    @Mock UserDirectoryClient userDirectoryClient;
    @Mock SchedulingClient schedulingClient;
    private MeetingService service;

    @BeforeEach void setUp() { service = new MeetingService(meetingRepository, participantRepository, userDirectoryClient,
            schedulingClient, new MeetingMapper(), 14); }

    private MeetingDtos.CreateMeetingRequest request(List<String> required, List<String> optional, int duration) {
        return new MeetingDtos.CreateMeetingRequest("Architecture Review", "API boundaries", duration, ORGANIZER, required, optional);
    }
    private MeetingEntity meeting(MeetingStatus status, int duration) {
        return new MeetingEntity(UUID.randomUUID(), "Architecture Review", "API boundaries", duration,
                ORGANIZER, status);
    }
    private void saveReturnsEntity() { when(meetingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0)); }

    @Test void createsRequestedMeetingAndNormalizesParticipantEmails() {
        saveReturnsEntity();

        var response = service.create(request(List.of("JOHN@example.com"), List.of("SARAH@example.com"), 45));

        ArgumentCaptor<MeetingEntity> captor = ArgumentCaptor.forClass(MeetingEntity.class);
        verify(meetingRepository).saveAndFlush(captor.capture());
        var persisted = captor.getValue();
        assertEquals(MeetingStatus.REQUESTED, response.status());
        assertEquals(2, response.participants().size());
        assertEquals("john@example.com", response.participants().get(0).email());
        assertEquals(ParticipantType.REQUIRED, response.participants().get(0).type());
        assertEquals(ParticipantType.OPTIONAL, response.participants().get(1).type());
        assertNotNull(response.participants().get(0).id());
        assertEquals(45, persisted.getDurationMinutes());
        assertEquals(MeetingType.STANDARD, persisted.getMeetingType());
    }

    @Test void acceptsAnExplicitMeetingType() {
        saveReturnsEntity();
        var request = new MeetingDtos.CreateMeetingRequest("Release", "Agenda", 30, ORGANIZER,
                List.of(), List.of(), MeetingType.VIRTUAL);

        var response = service.create(request);

        assertEquals(MeetingType.VIRTUAL, meetingRepositoryArgumentType());
    }

    private MeetingType meetingRepositoryArgumentType() {
        ArgumentCaptor<MeetingEntity> captor = ArgumentCaptor.forClass(MeetingEntity.class);
        verify(meetingRepository).saveAndFlush(captor.capture());
        return captor.getValue().getMeetingType();
    }

    @Test void rejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> service.create(request(List.of(), List.of(), 0)));
        verifyNoInteractions(userDirectoryClient, meetingRepository);
    }

    @Test void rejectsInvalidOrganizerEmail() {
        var bad = new MeetingDtos.CreateMeetingRequest("Title", "Agenda", 30, "not-an-email", List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.create(bad));
    }

    @Test void rejectsInvalidParticipantEmail() {
        assertThrows(IllegalArgumentException.class, () -> service.create(request(List.of("invalid"), List.of(), 30)));
        verifyNoInteractions(userDirectoryClient, meetingRepository);
    }

    @Test void rejectsDuplicateParticipantsAcrossRequiredAndOptionalLists() {
        var duplicate = request(List.of("JOHN@example.com"), List.of("john@example.com"), 30);
        assertThrows(IllegalArgumentException.class, () -> service.create(duplicate));
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void rejectsOrganizerAppearingAsParticipant() {
        assertThrows(IllegalArgumentException.class, () -> service.create(request(List.of(ORGANIZER), List.of(), 30)));
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void rejectsParticipantMissingFromUserDirectory() {
        doNothing().when(userDirectoryClient).requireExisting(ORGANIZER);
        doThrow(new ResourceNotFoundException("User was not found")).when(userDirectoryClient).requireExisting("unknown@example.com");
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(request(List.of("unknown@example.com"), List.of(), 30)));
    }

    @Test void rejectsOrganizerMissingFromUserDirectory() {
        doThrow(new ResourceNotFoundException("User was not found")).when(userDirectoryClient).requireExisting(ORGANIZER);
        assertThrows(ResourceNotFoundException.class, () -> service.create(request(List.of(), List.of(), 30)));
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void getsMeetingAndParticipants() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        var attendee = new MeetingParticipantEntity(UUID.randomUUID(), "john@example.com", ParticipantType.REQUIRED);
        entity.addParticipant(attendee);
        when(meetingRepository.findWithDetailsById(entity.getId())).thenReturn(Optional.of(entity));
        var response = service.get(entity.getId());
        assertEquals(entity.getId(), response.id());
        assertEquals("john@example.com", response.participants().getFirst().email());

        when(meetingRepository.existsById(entity.getId())).thenReturn(true);
        when(participantRepository.findByMeeting_IdOrderByIdAsc(entity.getId())).thenReturn(List.of(attendee));
        assertEquals("john@example.com", service.getParticipants(entity.getId()).getFirst().email());
    }

    @Test void scheduleRequestsSuggestionsForSavedMeetingWithoutConfirmingIt() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        entity.addParticipant(new MeetingParticipantEntity(UUID.randomUUID(), "bob@example.com", ParticipantType.REQUIRED));
        entity.addParticipant(new MeetingParticipantEntity(UUID.randomUUID(), "carol@example.com", ParticipantType.OPTIONAL));
        when(meetingRepository.findWithDetailsById(entity.getId())).thenReturn(Optional.of(entity));
        when(userDirectoryClient.timeZoneFor(ORGANIZER)).thenReturn(ZoneId.of("Asia/Kolkata"));
        var suggestion = new MeetingDtos.SlotSuggestionResponse(Instant.parse("2026-10-12T04:30:00Z"),
                Instant.parse("2026-10-12T05:00:00Z"), true, 1, 1, List.of());
        when(schedulingClient.findSlots(any())).thenReturn(List.of(suggestion));

        var response = service.schedule(entity.getId());

        assertEquals(entity.getId(), response.meetingId());
        assertEquals(List.of(suggestion), response.suggestions());
        assertEquals(MeetingStatus.REQUESTED, entity.getStatus());
        verify(meetingRepository, never()).save(any());
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(MeetingDtos.FindSlotsRequest.class);
        verify(schedulingClient).findSlots(requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertEquals(ORGANIZER, request.organizer());
        assertEquals(30, request.durationMinutes());
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1);
        assertEquals(List.of(ParticipantType.REQUIRED, ParticipantType.OPTIONAL),
                request.participants().stream().map(MeetingDtos.SchedulingParticipantRequest::type).toList());
    }

    @Test void schedulePassesPreferredDatesAndTimeAsOrganizerLocalValues() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        when(meetingRepository.findWithDetailsById(entity.getId())).thenReturn(Optional.of(entity));
        when(userDirectoryClient.timeZoneFor(ORGANIZER)).thenReturn(ZoneId.of("America/New_York"));
        when(schedulingClient.findSlots(any())).thenReturn(List.of());
        var preferences = new MeetingDtos.ScheduleMeetingRequest(LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-04"), java.time.LocalTime.of(10, 15));

        var response = service.schedule(entity.getId(), preferences);

        assertTrue(response.suggestions().isEmpty());
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(MeetingDtos.FindSlotsRequest.class);
        verify(schedulingClient).findSlots(requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertEquals(LocalDate.parse("2026-11-02"), request.startDate());
        assertEquals(LocalDate.parse("2026-11-04"), request.endDate());
        assertEquals(LocalDate.parse("2026-11-02"), request.preferredDate());
        assertEquals(java.time.LocalTime.of(10, 15), request.preferredStartTime());
        verify(userDirectoryClient).timeZoneFor(ORGANIZER);
    }

    @Test void schedulePassesExplicitPreferenceZoneToSchedulingService() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        when(meetingRepository.findWithDetailsById(entity.getId())).thenReturn(Optional.of(entity));
        when(userDirectoryClient.timeZoneFor(ORGANIZER)).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(schedulingClient.findSlots(any())).thenReturn(List.of());
        var preferences = new MeetingDtos.ScheduleMeetingRequest(LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-05"), java.time.LocalTime.of(9, 0), "America/Los_Angeles");

        service.schedule(entity.getId(), preferences);

        var requestCaptor = org.mockito.ArgumentCaptor.forClass(MeetingDtos.FindSlotsRequest.class);
        verify(schedulingClient).findSlots(requestCaptor.capture());
        assertEquals("America/Los_Angeles", requestCaptor.getValue().preferredTimeZone());
        assertEquals(LocalDate.parse("2026-10-05"), requestCaptor.getValue().startDate());
    }

    @Test void rejectsInvalidPreferenceZoneBeforeDownstreamCalls() {
        var preferences = new MeetingDtos.ScheduleMeetingRequest(LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-05"), java.time.LocalTime.of(9, 0), "Not/A_Zone");
        assertFalse(preferences.isPreferenceRangeValid());
        assertThrows(IllegalArgumentException.class, () -> service.schedule(UUID.randomUUID(), preferences));
        verifyNoInteractions(meetingRepository, schedulingClient, userDirectoryClient);
    }

    @Test void rejectsInvalidPreferredDateRangeBeforeCallingRepositories() {
        var invalidRange = new MeetingDtos.ScheduleMeetingRequest(LocalDate.parse("2026-11-04"),
                LocalDate.parse("2026-11-02"), null);

        assertThrows(IllegalArgumentException.class, () -> service.schedule(UUID.randomUUID(), invalidRange));
        verifyNoInteractions(meetingRepository, schedulingClient, userDirectoryClient);
    }

    @Test void schedulingMissingMeetingReturnsNotFoundAndNonRequestedMeetingConflicts() {
        var missing = UUID.randomUUID();
        when(meetingRepository.findWithDetailsById(missing)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.schedule(missing));

        var confirmed = meeting(MeetingStatus.CONFIRMED, 30);
        when(meetingRepository.findWithDetailsById(confirmed.getId())).thenReturn(Optional.of(confirmed));
        assertThrows(ConflictException.class, () -> service.schedule(confirmed.getId()));
        verifyNoInteractions(schedulingClient);
    }

    @Test void updatesRequestedMeetingDetailsAndParticipantsWithoutChangingIdentityOrCreationTime() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        var originalId = entity.getId();
        var createdAt = Instant.parse("2026-10-01T10:15:00Z");
        ReflectionTestUtils.setField(entity, "createdAt", createdAt);
        var oldParticipant = new MeetingParticipantEntity(UUID.randomUUID(), "old@example.com", ParticipantType.OPTIONAL);
        entity.addParticipant(oldParticipant);
        when(meetingRepository.findForUpdateById(originalId)).thenReturn(Optional.of(entity));
        when(meetingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(originalId, new MeetingDtos.CreateMeetingRequest("Updated title", "Updated agenda",
                45, "ORGANIZER@example.com", List.of("NEW@example.com"), List.of("guest@example.com"), MeetingType.VIRTUAL));

        assertEquals(originalId, response.id());
        assertEquals(createdAt, response.createdAt());
        assertEquals("Updated title", response.title());
        assertEquals("Updated agenda", response.agenda());
        assertEquals(45, response.durationMinutes());
        assertEquals("organizer@example.com", response.organizerEmail());
        assertEquals(MeetingStatus.REQUESTED, response.status());
        assertEquals(2, response.participants().size());
        assertEquals("new@example.com", response.participants().get(0).email());
        assertEquals(ParticipantType.REQUIRED, response.participants().get(0).type());
        assertEquals("guest@example.com", response.participants().get(1).email());
        assertEquals(ParticipantType.OPTIONAL, response.participants().get(1).type());
        assertFalse(response.participants().stream().anyMatch(p -> p.email().equals("old@example.com")));
        verify(meetingRepository).saveAndFlush(entity);
    }

    @Test void updateRetainsAnExistingParticipantInsteadOfReinsertingTheUniqueMeetingEmailPair() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        var existingBob = new MeetingParticipantEntity(UUID.randomUUID(), "bob@example.com", ParticipantType.REQUIRED);
        entity.addParticipant(existingBob);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        when(meetingRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var updated = service.update(entity.getId(), new MeetingDtos.CreateMeetingRequest("Updated title", "Updated agenda",
                30, ORGANIZER, List.of("BOB@example.com"), List.of()));

        assertEquals(1, updated.participants().size());
        assertEquals(existingBob.getId(), updated.participants().getFirst().id());
        assertEquals("bob@example.com", updated.participants().getFirst().email());
        verify(userDirectoryClient).requireExisting("bob@example.com");
        verify(meetingRepository).saveAndFlush(entity);
    }

    @Test void failedParticipantValidationLeavesTheManagedMeetingUnchanged() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        var originalParticipant = new MeetingParticipantEntity(UUID.randomUUID(), "bob@example.com", ParticipantType.REQUIRED);
        entity.addParticipant(originalParticipant);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        doNothing().when(userDirectoryClient).requireExisting(ORGANIZER);
        doThrow(new ResourceNotFoundException("User was not found"))
                .when(userDirectoryClient).requireExisting("missing@example.com");

        assertThrows(ResourceNotFoundException.class, () -> service.update(entity.getId(),
                new MeetingDtos.CreateMeetingRequest("Changed title", "Changed agenda", 45, ORGANIZER,
                        List.of("missing@example.com"), List.of())));

        assertEquals("Architecture Review", entity.getTitle());
        assertEquals("API boundaries", entity.getAgenda());
        assertEquals(30, entity.getDurationMinutes());
        assertEquals(1, entity.getParticipants().size());
        assertSame(originalParticipant, entity.getParticipants().getFirst());
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void rejectsInvalidUpdateRequestsBeforeLookingUpMeetingOrUsers() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.update(id, request(List.of(), List.of(), 0)));
        assertThrows(IllegalArgumentException.class, () -> service.update(id,
                request(List.of("not-an-email"), List.of(), 30)));
        assertThrows(IllegalArgumentException.class, () -> service.update(id,
                request(List.of("john@example.com"), List.of("JOHN@example.com"), 30)));
        assertThrows(IllegalArgumentException.class, () -> service.update(id,
                request(List.of(ORGANIZER), List.of(), 30)));
        verifyNoInteractions(meetingRepository, userDirectoryClient);
    }

    @Test void rejectsUpdatingMeetingThatDoesNotExist() {
        var id = UUID.randomUUID();
        when(meetingRepository.findForUpdateById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.update(id, request(List.of(), List.of(), 30)));
    }

    @Test void rejectsUpdatingMeetingAfterItLeavesRequestedState() {
        var entity = meeting(MeetingStatus.CONFIRMED, 30);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(entity.getId(), request(List.of(), List.of(), 30)));
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void getMeetingAndParticipantsReturnNotFoundWhenMeetingDoesNotExist() {
        var missing = UUID.randomUUID();
        when(meetingRepository.findWithDetailsById(missing)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get(missing));
        when(meetingRepository.existsById(missing)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getParticipants(missing));
    }

    @Test void confirmsRequestedMeetingWithExactDuration() {
        var entity = meeting(MeetingStatus.REQUESTED, 60);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        var start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        var end = start.plusSeconds(3600);
        when(userDirectoryClient.timeZoneFor(ORGANIZER)).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(schedulingClient.findSlots(any())).thenReturn(List.of(suggestion(start, end, true)));
        saveReturnsEntity();
        var response = service.confirm(entity.getId(), new MeetingDtos.ConfirmMeetingRequest(
                start, end));
        assertEquals(MeetingStatus.CONFIRMED, response.status());
        assertEquals(start, response.confirmedStart());
        assertEquals(end, response.confirmedEnd());
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(MeetingDtos.FindSlotsRequest.class);
        verify(schedulingClient).findSlots(requestCaptor.capture());
        assertEquals(start.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate(), requestCaptor.getValue().startDate());
        assertEquals(start.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate(), requestCaptor.getValue().endDate());
        assertEquals(start.atZone(ZoneId.of("Asia/Kolkata")).toLocalTime(), requestCaptor.getValue().preferredStartTime());
    }

    @Test void rejectsPastConfirmationWithoutCallingSchedulingOrSaving() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        var start = Instant.now().minus(Duration.ofDays(1));

        assertThrows(ConflictException.class, () -> service.confirm(entity.getId(),
                new MeetingDtos.ConfirmMeetingRequest(start, start.plusSeconds(1800))));

        assertEquals(MeetingStatus.REQUESTED, entity.getStatus());
        assertNull(entity.getConfirmedStart());
        assertNull(entity.getConfirmedEnd());
        verifyNoInteractions(userDirectoryClient, schedulingClient);
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void rejectsSlotNotReturnedAsAvailableForRequiredParticipants() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        entity.addParticipant(new MeetingParticipantEntity(UUID.randomUUID(), "bob@example.com", ParticipantType.REQUIRED));
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        var start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        when(userDirectoryClient.timeZoneFor(ORGANIZER)).thenReturn(ZoneId.of("America/New_York"));
        when(schedulingClient.findSlots(any())).thenReturn(List.of(suggestion(start, start.plusSeconds(1800), false)));

        assertThrows(ConflictException.class, () -> service.confirm(entity.getId(),
                new MeetingDtos.ConfirmMeetingRequest(start, start.plusSeconds(1800))));

        assertEquals(MeetingStatus.REQUESTED, entity.getStatus());
        assertNull(entity.getConfirmedStart());
        assertNull(entity.getConfirmedEnd());
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void leavesMeetingUnchangedWhenAvailabilityDependencyFails() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        var start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        when(userDirectoryClient.timeZoneFor(ORGANIZER)).thenReturn(ZoneId.of("Europe/London"));
        when(schedulingClient.findSlots(any())).thenThrow(new ServiceUnavailableException("Scheduling Service request failed", null));

        assertThrows(ServiceUnavailableException.class, () -> service.confirm(entity.getId(),
                new MeetingDtos.ConfirmMeetingRequest(start, start.plusSeconds(1800))));

        assertEquals(MeetingStatus.REQUESTED, entity.getStatus());
        assertNull(entity.getConfirmedStart());
        assertNull(entity.getConfirmedEnd());
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void leavesMeetingUnchangedWhenOrganizerTimeZoneLookupFails() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        var start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        when(userDirectoryClient.timeZoneFor(ORGANIZER))
                .thenThrow(new ServiceUnavailableException("User Service request failed", null));

        assertThrows(ServiceUnavailableException.class, () -> service.confirm(entity.getId(),
                new MeetingDtos.ConfirmMeetingRequest(start, start.plusSeconds(1800))));

        assertEquals(MeetingStatus.REQUESTED, entity.getStatus());
        assertNull(entity.getConfirmedStart());
        assertNull(entity.getConfirmedEnd());
        verifyNoInteractions(schedulingClient);
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    private MeetingDtos.SlotSuggestionResponse suggestion(Instant start, Instant end, boolean requiredAvailable) {
        return new MeetingDtos.SlotSuggestionResponse(start, end, requiredAvailable, 0, 0, List.of());
    }

    @Test void rejectsConfirmationWithWrongDurationOrInvalidRange() {
        var entity = meeting(MeetingStatus.REQUESTED, 60);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        var start = Instant.parse("2026-10-05T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.confirm(entity.getId(),
                new MeetingDtos.ConfirmMeetingRequest(start, start.plusSeconds(1800))));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(entity.getId(),
                new MeetingDtos.ConfirmMeetingRequest(start, start)));
        assertEquals(MeetingStatus.REQUESTED, entity.getStatus());
    }

    @Test void rejectsConfirmingAlreadyConfirmedMeeting() {
        var entity = meeting(MeetingStatus.CONFIRMED, 60);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        var start = Instant.parse("2026-10-05T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> service.confirm(entity.getId(),
                new MeetingDtos.ConfirmMeetingRequest(start, start.plusSeconds(3600))));
        verify(meetingRepository, never()).saveAndFlush(any());
    }

    @Test void cancelsMeetingAndCancellationIsIdempotent() {
        var entity = meeting(MeetingStatus.REQUESTED, 30);
        when(meetingRepository.findForUpdateById(entity.getId())).thenReturn(Optional.of(entity));
        saveReturnsEntity();
        assertEquals(MeetingStatus.CANCELLED, service.cancel(entity.getId()).status());
        assertEquals(MeetingStatus.CANCELLED, service.cancel(entity.getId()).status());
        verify(meetingRepository, times(2)).saveAndFlush(entity);
    }
}
