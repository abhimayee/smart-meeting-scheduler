package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class CalendarAvailabilityClientTest {
    @Test void returnsUtcAvailabilityFromCalendarBoundary() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://calendar/api/calendar/users/a%40example.com/availability?start=2026-10-05T00:00:00Z&end=2026-10-06T00:00:00Z"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"userEmail\":\"a@example.com\",\"timeZone\":\"Asia/Kolkata\",\"rangeStart\":\"2026-10-05T00:00:00Z\",\"rangeEnd\":\"2026-10-06T00:00:00Z\",\"freeIntervals\":[{\"start\":\"2026-10-05T04:00:00Z\",\"end\":\"2026-10-05T05:00:00Z\"}]}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        var client = new CalendarAvailabilityClient(builder, "http://calendar");
        var result = client.forInstantRange("a@example.com", Instant.parse("2026-10-05T00:00:00Z"), Instant.parse("2026-10-06T00:00:00Z"));
        assertEquals("Asia/Kolkata", result.timeZone().getId());
        assertEquals(Instant.parse("2026-10-05T04:00:00Z"), result.freeIntervals().getFirst().start());
        server.verify();
    }

    @Test void mapsCalendarFailureToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> request.getURI().getPath().contains("availability/dates"))
                .andRespond(withServerError());
        var client = new CalendarAvailabilityClient(builder, "http://calendar");
        assertThrows(ServiceUnavailableException.class, () -> client.forDateRange("a@example.com",
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06")));
        server.verify();
    }

    @Test void mapsCalendarReadTimeoutToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> request.getURI().getPath().contains("availability/dates"))
                .andRespond(withException(new IOException("Read timed out")));
        var client = new CalendarAvailabilityClient(builder, "http://calendar");

        assertThrows(ServiceUnavailableException.class, () -> client.forDateRange("a@example.com",
                LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-06")));
        server.verify();
    }
}
