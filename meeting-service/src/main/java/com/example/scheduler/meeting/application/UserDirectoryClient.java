package com.example.scheduler.meeting.application;

import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.time.ZoneId;
import java.time.DateTimeException;

@Component
public class UserDirectoryClient {
    private final RestClient client;

    public UserDirectoryClient(RestClient.Builder builder, @Value("${user-service.url}") String baseUrl) {
        client = builder.baseUrl(baseUrl).build();
    }

    public void requireExisting(String email) {
        try {
            client.get().uri(uri -> uri.path("/api/users/{email}").build(email))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("User was not found: " + email);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("User Service request failed", ex);
        }
    }

    public ZoneId timeZoneFor(String email) {
        try {
            var response = client.get().uri(uri -> uri.path("/api/users/{email}").build(email))
                    .retrieve().body(UserTimeZoneResponse.class);
            if (response == null || response.timeZone() == null)
                throw new ServiceUnavailableException("User Service returned an incomplete profile", null);
            return ZoneId.of(response.timeZone());
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("User was not found: " + email);
        } catch (RestClientException | DateTimeException ex) {
            throw new ServiceUnavailableException("User Service request failed", ex);
        }
    }

    private record UserTimeZoneResponse(String timeZone) { }
}
