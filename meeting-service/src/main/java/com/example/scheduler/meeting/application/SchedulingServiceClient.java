package com.example.scheduler.meeting.application;

import com.example.scheduler.meeting.api.MeetingDtos;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SchedulingServiceClient implements SchedulingClient {
    private final RestClient client;

    public SchedulingServiceClient(RestClient.Builder builder,
                                   @Value("${scheduling-service.url}") String baseUrl) {
        client = builder.baseUrl(baseUrl).build();
    }

    @Override
    public List<MeetingDtos.SlotSuggestionResponse> findSlots(MeetingDtos.FindSlotsRequest request) {
        try {
            var response = client.post().uri("/api/scheduling/find-slots").body(request)
                    .retrieve().body(SchedulingResponse.class);
            if (response == null || response.suggestions() == null)
                throw new ServiceUnavailableException("Scheduling Service returned an incomplete response", null);
            return response.suggestions();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("A meeting participant was not found by Scheduling Service");
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Scheduling Service request failed", ex);
        }
    }

    private record SchedulingResponse(List<MeetingDtos.SlotSuggestionResponse> suggestions) { }
}
