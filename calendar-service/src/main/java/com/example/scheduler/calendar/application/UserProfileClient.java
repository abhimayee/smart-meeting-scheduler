package com.example.scheduler.calendar.application;

import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserProfileClient {
    private final RestClient client;

    public UserProfileClient(RestClient.Builder builder, @Value("${user-service.url}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public UserProfileResponse get(String email) {
        try {
            return client.get().uri(uri -> uri.path("/api/users/{email}").build(email))
                    .retrieve().body(UserProfileResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("User was not found");
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("User Service request failed", ex);
        }
    }

    public record UserProfileResponse(UUID id, String email, String displayName, String timeZone,
                                      List<WorkingHoursResponse> workingHours, Instant createdAt, Instant updatedAt) { }
    public record WorkingHoursResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) { }
}
