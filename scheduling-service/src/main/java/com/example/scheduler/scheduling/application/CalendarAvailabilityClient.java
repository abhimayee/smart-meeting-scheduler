package com.example.scheduler.scheduling.application;

import com.example.scheduler.scheduling.domain.CalendarAvailabilitySnapshot;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.FreeInterval;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CalendarAvailabilityClient {
    private final RestClient client;

    public CalendarAvailabilityClient(RestClient.Builder builder, @Value("${calendar-service.url}") String baseUrl) {
        client = builder.baseUrl(baseUrl).build();
    }

    public CalendarAvailabilitySnapshot forDateRange(String email, LocalDate start, LocalDate end) {
        return read(email, request -> request.uri(uri -> uri.path("/api/calendar/users/{email}/availability/dates")
                .queryParam("startDate", start).queryParam("endDate", end).build(email)));
    }

    public CalendarAvailabilitySnapshot forInstantRange(String email, Instant start, Instant end) {
        return read(email, request -> request.uri(uri -> uri.path("/api/calendar/users/{email}/availability")
                .queryParam("start", start).queryParam("end", end).build(email)));
    }

    private CalendarAvailabilitySnapshot read(String email, java.util.function.Function<RestClient.RequestHeadersUriSpec<?>, RestClient.RequestHeadersSpec<?>> request) {
        try {
            AvailabilityResponse response = request.apply(client.get()).retrieve().body(AvailabilityResponse.class);
            List<FreeInterval> free = response.freeIntervals().stream().map(interval -> new FreeInterval(interval.start(), interval.end())).toList();
            return new CalendarAvailabilitySnapshot(new EmailAddress(response.userEmail()), ZoneId.of(response.timeZone()),
                    response.rangeStart(), response.rangeEnd(), free);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("User was not found: " + email);
        } catch (RestClientException | java.time.DateTimeException ex) {
            throw new ServiceUnavailableException("Calendar Service request failed", ex);
        }
    }

    private record AvailabilityResponse(String userEmail, String timeZone, Instant rangeStart, Instant rangeEnd,
                                        List<FreeIntervalResponse> freeIntervals) { }
    private record FreeIntervalResponse(Instant start, Instant end) { }
}
