package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.scheduling.api.SchedulingDtos;
import com.example.scheduler.scheduling.domain.ParticipantAvailability;
import com.example.scheduler.scheduling.domain.CalendarAvailabilitySnapshot;
import com.example.scheduler.scheduling.domain.SlotSuggestion;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.InstantRange;
import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SchedulingService {
    private final CalendarAvailabilityClient availabilityService;
    private final AvailabilityIntersectionService intersectionService;
    private final SlotGenerationService slotGenerationService;
    private final SlotRankingService rankingService;

    public SchedulingService(CalendarAvailabilityClient availabilityService,
                             AvailabilityIntersectionService intersectionService,
                             SlotGenerationService slotGenerationService,
                             SlotRankingService rankingService) {
        this.availabilityService = availabilityService;
        this.intersectionService = intersectionService;
        this.slotGenerationService = slotGenerationService;
        this.rankingService = rankingService;
    }

    public List<SlotSuggestion> findSlots(SchedulingDtos.FindSlotsRequest request) {
        validateRequest(request);
        var organizer = new EmailAddress(request.organizer());
        var participants = participants(request, organizer);

        ZoneId requestedPreferenceZone = preferenceZone(request.preferredTimeZone());
        // Explicitly zoned preference dates are inclusive local dates. Convert their boundaries
        // independently so DST transitions produce the correct UTC window length.
        CalendarAvailabilitySnapshot organizerWindow;
        if (requestedPreferenceZone != null && request.preferredDate() != null) {
            Instant rangeStart = request.startDate().atStartOfDay(requestedPreferenceZone).toInstant();
            Instant rangeEnd = request.endDate().plusDays(1).atStartOfDay(requestedPreferenceZone).toInstant();
            organizerWindow = availabilityService.forInstantRange(organizer.value(), rangeStart, rangeEnd);
        } else {
            // Without explicitly zoned date preferences, preserve the existing organizer-local date behavior.
            organizerWindow = availabilityService.forDateRange(organizer.value(), request.startDate(), request.endDate());
        }
        var windows = new LinkedHashMap<EmailAddress, CalendarAvailabilitySnapshot>();
        windows.put(organizer, organizerWindow);
        for (var participant : participants) {
            if (participant.email().equals(organizer)) continue;
            windows.put(participant.email(), availabilityService.forInstantRange(participant.email().value(),
                    organizerWindow.rangeStart(), organizerWindow.rangeEnd()));
        }

        var requiredFreeIntervals = participants.stream()
                .filter(participant -> participant.type() == ParticipantType.REQUIRED)
                .map(participant -> windows.get(participant.email()).freeIntervals())
                .toList();
        List<InstantRange> commonRequiredIntervals = intersectionService.intersect(requiredFreeIntervals);
        List<InstantRange> candidates = slotGenerationService.generate(commonRequiredIntervals,
                Duration.ofMinutes(request.durationMinutes()));
        if (candidates.isEmpty()) return List.of();

        var participantFreeIntervals = new LinkedHashMap<EmailAddress, List<InstantRange>>();
        for (var participant : participants)
            participantFreeIntervals.put(participant.email(), intersectionService.normalize(windows.get(participant.email()).freeIntervals()));
        boolean[][] availability = evaluateParticipants(candidates, participants, participantFreeIntervals);

        var suggestions = new ArrayList<SlotSuggestion>();
        int optionalTotal = (int) participants.stream().filter(p -> p.type() == ParticipantType.OPTIONAL).count();
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            var candidate = candidates.get(candidateIndex);
            var attendeeDetails = new ArrayList<ParticipantAvailability>();
            int optionalAvailable = 0;
            boolean allRequiredAvailable = true;
            for (int participantIndex = 0; participantIndex < participants.size(); participantIndex++) {
                var participant = participants.get(participantIndex);
                boolean available = availability[participantIndex][candidateIndex];
                if (participant.type() == ParticipantType.REQUIRED) allRequiredAvailable &= available;
                else if (available) optionalAvailable++;
                ZoneId zone = windows.get(participant.email()).timeZone();
                attendeeDetails.add(new ParticipantAvailability(participant.email(), participant.type(), available,
                        candidate.start().atZone(zone), candidate.end().atZone(zone)));
            }
            if (allRequiredAvailable)
                suggestions.add(new SlotSuggestion(candidate, true, optionalAvailable, optionalTotal, attendeeDetails));
        }
        ZoneId rankingZone = requestedPreferenceZone == null ? organizerWindow.timeZone() : requestedPreferenceZone;
        return rankingService.rank(suggestions, organizer, organizerWindow.timeZone(), rankingZone,
                request.preferredDate(), request.preferredStartTime());
    }

    /** Evaluates time-sorted candidates against each attendee's intervals with a forward-only cursor. */
    private boolean[][] evaluateParticipants(List<InstantRange> candidates, List<Participant> participants,
                                               Map<EmailAddress, List<InstantRange>> freeIntervals) {
        boolean[][] result = new boolean[participants.size()][candidates.size()];
        for (int participantIndex = 0; participantIndex < participants.size(); participantIndex++) {
            var intervals = freeIntervals.get(participants.get(participantIndex).email());
            int intervalIndex = 0;
            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                var candidate = candidates.get(candidateIndex);
                while (intervalIndex < intervals.size() && !intervals.get(intervalIndex).end().isAfter(candidate.start()))
                    intervalIndex++;
                result[participantIndex][candidateIndex] = intervalIndex < intervals.size()
                        && !intervals.get(intervalIndex).start().isAfter(candidate.start())
                        && !intervals.get(intervalIndex).end().isBefore(candidate.end());
            }
        }
        return result;
    }

    private List<Participant> participants(SchedulingDtos.FindSlotsRequest request, EmailAddress organizer) {
        var seen = new HashSet<EmailAddress>();
        seen.add(organizer);
        var participants = new ArrayList<Participant>();
        participants.add(new Participant(organizer, ParticipantType.REQUIRED));
        if (request.participants() == null) return List.copyOf(participants);
        for (var input : request.participants()) {
            if (input == null || input.email() == null || input.type() == null)
                throw new IllegalArgumentException("Each participant requires an email and participant type");
            var email = new EmailAddress(input.email());
            if (!seen.add(email)) throw new IllegalArgumentException("Participant emails must be unique and must not repeat the organizer");
            participants.add(new Participant(email, input.type()));
        }
        return List.copyOf(participants);
    }

    private void validateRequest(SchedulingDtos.FindSlotsRequest request) {
        if (request == null) throw new IllegalArgumentException("Scheduling request is required");
        preferenceZone(request.preferredTimeZone());
        if (request.organizer() == null || request.organizer().isBlank()) throw new IllegalArgumentException("Organizer is required");
        new EmailAddress(request.organizer());
        if (request.durationMinutes() <= 0) throw new IllegalArgumentException("Meeting duration must be positive");
        if (request.startDate() == null || request.endDate() == null || request.endDate().isBefore(request.startDate()))
            throw new IllegalArgumentException("A valid startDate and endDate are required");
        if (request.preferredDate() != null && (request.preferredDate().isBefore(request.startDate())
                || request.preferredDate().isAfter(request.endDate())))
            throw new IllegalArgumentException("preferredDate must fall within the requested date range");
    }

    private ZoneId preferenceZone(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            ZoneId zone = ZoneId.of(value.trim());
            if (!ZoneId.getAvailableZoneIds().contains(zone.getId()))
                throw new IllegalArgumentException("preferredTimeZone must be an IANA time-zone ID");
            return zone;
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("preferredTimeZone must be a valid IANA time-zone ID", ex);
        }
    }

    private record Participant(EmailAddress email, ParticipantType type) { }
}
