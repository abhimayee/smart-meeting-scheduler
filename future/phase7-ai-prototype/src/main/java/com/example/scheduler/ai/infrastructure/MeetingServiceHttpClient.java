package com.example.scheduler.ai.infrastructure;

import com.example.scheduler.ai.application.MeetingWorkflowClient;
import com.example.scheduler.ai.domain.MeetingRequestDetails;
import com.example.scheduler.ai.domain.MeetingSchedulePreferences;
import com.example.scheduler.ai.domain.MeetingSuggestion;
import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MeetingServiceHttpClient implements MeetingWorkflowClient {
    private final RestClient client;

    public MeetingServiceHttpClient(RestClient.Builder builder,
                                    @Value("${meeting-service.url}") String meetingServiceUrl) {
        this.client = builder.baseUrl(meetingServiceUrl).build();
    }

    @Override
    public UUID createMeeting(MeetingRequestDetails request) {
        try {
            var response = client.post().uri("/api/meetings")
                    .body(new CreateMeetingRequest(request.title(), String.join("; ", request.agenda()),
                            request.durationMinutes(), request.organizerEmail(), request.requiredParticipants(),
                            request.optionalParticipants()))
                    .retrieve().body(CreatedMeetingResponse.class);
            if (response == null || response.id() == null)
                throw new ServiceUnavailableException("Meeting Service returned an incomplete create response", null);
            return response.id();
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Meeting Service create request failed", ex);
        }
    }

    @Override
    public List<MeetingSuggestion> requestSchedulingSuggestions(UUID meetingId, MeetingSchedulePreferences preferences) {
        try {
            var request = client.post().uri("/api/meetings/{meetingId}/schedule", meetingId);
            if (preferences != null && !preferences.isEmpty()) {
                request.body(new ScheduleMeetingRequest(date(preferences.preferredStartDate()),
                        date(preferences.preferredEndDate()), time(preferences.preferredStartTime()),
                        preferences.preferredTimeZone() == null ? null : preferences.preferredTimeZone().getId()));
            }
            var response = request.retrieve().body(ScheduleMeetingResponse.class);
            if (response == null || response.meetingId() == null || !meetingId.equals(response.meetingId())
                    || response.suggestions() == null)
                throw new ServiceUnavailableException("Meeting Service returned an incomplete scheduling response", null);
            return response.suggestions().stream().map(MeetingServiceHttpClient::toDomain).toList();
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Meeting Service scheduling request failed", ex);
        }
    }

    private static MeetingSuggestion toDomain(SlotSuggestionResponse slot) {
        return new MeetingSuggestion(slot.start(), slot.end(), slot.requiredParticipantsAvailable(),
                slot.optionalParticipantsAvailable(), slot.optionalParticipantsTotal(),
                slot.participants() == null ? List.of() : slot.participants().stream()
                        .map(participant -> new MeetingSuggestion.ParticipantSuggestion(participant.email(),
                                participant.type(), participant.available(), participant.localStart(),
                                participant.localEnd())).toList());
    }

    private static String date(LocalDate value) { return value == null ? null : value.toString(); }
    private static String time(LocalTime value) { return value == null ? null : value.toString(); }

    private record CreateMeetingRequest(String title, String agenda, int durationMinutes, String organizerEmail,
                                        List<String> requiredParticipants, List<String> optionalParticipants) { }
    private record ScheduleMeetingRequest(String preferredStartDate, String preferredEndDate,
                                          String preferredStartTime, String preferredTimeZone) { }
    private record CreatedMeetingResponse(UUID id) { }
    private record ScheduleMeetingResponse(UUID meetingId, List<SlotSuggestionResponse> suggestions) { }
    private record SlotSuggestionResponse(Instant start, Instant end, boolean requiredParticipantsAvailable,
                                          int optionalParticipantsAvailable, int optionalParticipantsTotal,
                                          List<ParticipantSlotResponse> participants) { }
    private record ParticipantSlotResponse(String email, ParticipantType type, boolean available,
                                           String localStart, String localEnd) { }
}
