package com.example.scheduler.meeting.application;

import com.example.scheduler.meeting.api.MeetingDtos;
import com.example.scheduler.meeting.domain.Meeting;
import com.example.scheduler.meeting.domain.MeetingStatus;
import com.example.scheduler.meeting.domain.MeetingType;
import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingEntity;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingParticipantEntity;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingParticipantRepository;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingRepository;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ConflictException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository participantRepository;
    private final UserDirectoryClient userDirectoryClient;
    private final SchedulingClient schedulingClient;
    private final MeetingMapper mapper;
    private final int schedulingSearchWindowDays;

    public MeetingService(MeetingRepository meetingRepository, MeetingParticipantRepository participantRepository,
                          UserDirectoryClient userDirectoryClient, SchedulingClient schedulingClient,
                          MeetingMapper mapper,
                          @Value("${meeting.scheduling-search-window-days:14}") int schedulingSearchWindowDays) {
        this.meetingRepository = meetingRepository;
        this.participantRepository = participantRepository;
        this.userDirectoryClient = userDirectoryClient;
        this.schedulingClient = schedulingClient;
        this.mapper = mapper;
        if (schedulingSearchWindowDays < 1) throw new IllegalArgumentException("Scheduling search window must be positive");
        this.schedulingSearchWindowDays = schedulingSearchWindowDays;
    }

    @Transactional
    public MeetingDtos.MeetingResponse create(MeetingDtos.CreateMeetingRequest request) {
        validateRequest(request);
        var organizerEmail = new EmailAddress(request.organizerEmail());
        // Validate and normalize every attendee before making any user repository calls.
        var requiredEmails = normalizeEmails(request.requiredParticipants());
        var optionalEmails = normalizeEmails(request.optionalParticipants());
        userDirectoryClient.requireExisting(organizerEmail.value());
        var participantEmails = new HashSet<EmailAddress>();
        participantEmails.add(organizerEmail);
        var meeting = new MeetingEntity(UUID.randomUUID(), request.title().trim(), request.agenda().trim(),
                request.durationMinutes(), organizerEmail.value(),
                request.meetingType() == null ? MeetingType.STANDARD : request.meetingType(),
                MeetingStatus.REQUESTED);
        addParticipants(meeting, requiredEmails, ParticipantType.REQUIRED, participantEmails);
        addParticipants(meeting, optionalEmails, ParticipantType.OPTIONAL, participantEmails);
        return mapper.toResponse(meetingRepository.saveAndFlush(meeting));
    }

    @Transactional
    public MeetingDtos.MeetingResponse update(UUID meetingId, MeetingDtos.CreateMeetingRequest request) {
        validateRequest(request);
        var organizerEmail = new EmailAddress(request.organizerEmail());
        var requiredEmails = normalizeEmails(request.requiredParticipants());
        var optionalEmails = normalizeEmails(request.optionalParticipants());
        validateParticipantEmails(organizerEmail, requiredEmails, optionalEmails);

        var meeting = meetingRepository.findForUpdateById(meetingId).orElseThrow(this::meetingNotFound);
        if (meeting.getStatus() != MeetingStatus.REQUESTED) {
            throw new IllegalArgumentException("Only requested meetings can be updated");
        }

        userDirectoryClient.requireExisting(organizerEmail.value());
        var updatedParticipants = new ArrayList<MeetingParticipantEntity>();
        var seen = new HashSet<EmailAddress>();
        seen.add(organizerEmail);
        updatedParticipants.addAll(toParticipants(requiredEmails, ParticipantType.REQUIRED, seen));
        updatedParticipants.addAll(toParticipants(optionalEmails, ParticipantType.OPTIONAL, seen));
        meeting.updateDetails(request.title().trim(), request.agenda().trim(), request.durationMinutes(),
                organizerEmail.value(), request.meetingType());
        meeting.replaceParticipants(updatedParticipants);
        return mapper.toResponse(meetingRepository.saveAndFlush(meeting));
    }

    @Transactional(readOnly = true)
    public MeetingDtos.MeetingResponse get(UUID meetingId) {
        return mapper.toResponse(findWithDetails(meetingId));
    }

    @Transactional(readOnly = true)
    public MeetingDtos.ScheduleMeetingResponse schedule(UUID meetingId) {
        return schedule(meetingId, null);
    }

    @Transactional(readOnly = true)
    public MeetingDtos.ScheduleMeetingResponse schedule(UUID meetingId, MeetingDtos.ScheduleMeetingRequest preferences) {
        if (preferences != null) preferences.validate();
        var meeting = findWithDetails(meetingId);
        if (meeting.getStatus() != MeetingStatus.REQUESTED)
            throw new ConflictException("Only requested meetings can search for scheduling suggestions");

        var organizerZone = userDirectoryClient.timeZoneFor(meeting.getOrganizerEmail());
        var startDate = LocalDate.now(organizerZone);
        var searchStartDate = preferences != null && preferences.preferredStartDate() != null
                ? preferences.preferredStartDate() : startDate;
        var searchEndDate = preferences != null && preferences.preferredEndDate() != null
                ? preferences.preferredEndDate()
                : startDate.plusDays(schedulingSearchWindowDays - 1L);
        var request = new MeetingDtos.FindSlotsRequest(meeting.getOrganizerEmail(),
                meeting.getParticipants().stream().map(participant -> new MeetingDtos.SchedulingParticipantRequest(
                        participant.getEmail(), participant.getType())).toList(),
                meeting.getDurationMinutes(), searchStartDate, searchEndDate,
                preferences == null ? null : preferences.preferredStartDate(),
                preferences == null ? null : preferences.preferredStartTime(),
                preferences == null ? null : preferences.preferredTimeZone());
        return new MeetingDtos.ScheduleMeetingResponse(meetingId, schedulingClient.findSlots(request));
    }

    @Transactional(readOnly = true)
    public List<MeetingDtos.ParticipantResponse> getParticipants(UUID meetingId) {
        if (!meetingRepository.existsById(meetingId)) throw meetingNotFound();
        return participantRepository.findByMeeting_IdOrderByIdAsc(meetingId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public MeetingDtos.MeetingResponse confirm(UUID meetingId, MeetingDtos.ConfirmMeetingRequest request) {
        if (request == null) throw new IllegalArgumentException("Confirmation range is required");
        var entity = meetingRepository.findForUpdateById(meetingId).orElseThrow(this::meetingNotFound);
        var meeting = mapper.toDomain(entity);
        Meeting confirmed = meeting.confirm(request.start(), request.end());
        if (!request.start().isAfter(Instant.now()))
            throw new ConflictException("Meeting start must be in the future");

        var organizerZone = userDirectoryClient.timeZoneFor(meeting.organizerEmail());
        var localStart = request.start().atZone(organizerZone);
        var localEnd = request.end().minusNanos(1).atZone(organizerZone);
        var searchRequest = new MeetingDtos.FindSlotsRequest(meeting.organizerEmail(),
                meeting.participants().stream().map(participant -> new MeetingDtos.SchedulingParticipantRequest(
                        participant.email().value(), participant.type())).toList(),
                meeting.durationMinutes(), localStart.toLocalDate(),
                localEnd.toLocalDate(), localStart.toLocalDate(), localStart.toLocalTime());
        boolean available = schedulingClient.findSlots(searchRequest).stream()
                .anyMatch(slot -> slot != null && slot.requiredParticipantsAvailable()
                        && request.start().equals(slot.start()) && request.end().equals(slot.end()));
        if (!available || !request.start().isAfter(Instant.now()))
            throw new ConflictException("Selected meeting slot is no longer available");

        entity.applyState(confirmed.status(), confirmed.confirmedStart(), confirmed.confirmedEnd());
        return mapper.toResponse(meetingRepository.saveAndFlush(entity));
    }

    @Transactional
    public MeetingDtos.MeetingResponse cancel(UUID meetingId) {
        var entity = meetingRepository.findForUpdateById(meetingId).orElseThrow(this::meetingNotFound);
        Meeting cancelled = mapper.toDomain(entity).cancel();
        entity.applyState(cancelled.status(), cancelled.confirmedStart(), cancelled.confirmedEnd());
        return mapper.toResponse(meetingRepository.saveAndFlush(entity));
    }

    private List<EmailAddress> normalizeEmails(List<String> emails) {
        if (emails == null) return List.of();
        var normalized = new ArrayList<EmailAddress>(emails.size());
        for (String rawEmail : emails) {
            normalized.add(new EmailAddress(rawEmail));
        }
        return List.copyOf(normalized);
    }

    private void validateRequest(MeetingDtos.CreateMeetingRequest request) {
        if (request == null) throw new IllegalArgumentException("Meeting request is required");
        if (request.title() == null || request.title().isBlank()) throw new IllegalArgumentException("Meeting title is required");
        if (request.title().trim().length() > 300) throw new IllegalArgumentException("Meeting title must not exceed 300 characters");
        if (request.agenda() == null || request.agenda().isBlank()) throw new IllegalArgumentException("Meeting agenda is required");
        if (request.durationMinutes() <= 0) throw new IllegalArgumentException("Meeting duration must be positive");
    }

    private void validateParticipantEmails(EmailAddress organizerEmail,
                                           List<EmailAddress> requiredEmails,
                                           List<EmailAddress> optionalEmails) {
        var seen = new HashSet<EmailAddress>();
        seen.add(organizerEmail);
        for (var email : requiredEmails) {
            if (!seen.add(email))
                throw new IllegalArgumentException("Participant emails must be unique and must not include the organizer");
        }
        for (var email : optionalEmails) {
            if (!seen.add(email))
                throw new IllegalArgumentException("Participant emails must be unique and must not include the organizer");
        }
    }

    private void addParticipants(MeetingEntity meeting, List<EmailAddress> emails, ParticipantType type,
                                 Set<EmailAddress> seen) {
        for (EmailAddress email : emails) {
            if (!seen.add(email))
                throw new IllegalArgumentException("Participant emails must be unique and must not include the organizer");
            userDirectoryClient.requireExisting(email.value());
            meeting.addParticipant(new MeetingParticipantEntity(UUID.randomUUID(), email.value(), type));
        }
    }

    private List<MeetingParticipantEntity> toParticipants(List<EmailAddress> emails, ParticipantType type,
                                                          Set<EmailAddress> seen) {
        var participants = new ArrayList<MeetingParticipantEntity>(emails.size());
        for (EmailAddress email : emails) {
            if (!seen.add(email))
                throw new IllegalArgumentException("Participant emails must be unique and must not include the organizer");
            userDirectoryClient.requireExisting(email.value());
            participants.add(new MeetingParticipantEntity(UUID.randomUUID(), email.value(), type));
        }
        return participants;
    }

    private MeetingEntity findWithDetails(UUID meetingId) {
        return meetingRepository.findWithDetailsById(meetingId).orElseThrow(this::meetingNotFound);
    }

    private ResourceNotFoundException meetingNotFound() { return new ResourceNotFoundException("Meeting was not found"); }
}
