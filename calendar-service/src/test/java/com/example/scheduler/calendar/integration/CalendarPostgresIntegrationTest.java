package com.example.scheduler.calendar.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.scheduler.CalendarServiceApplication;
import com.example.scheduler.test.support.IntegrationTestDatabaseUrl;
import com.example.scheduler.calendar.api.CalendarDtos;
import com.example.scheduler.calendar.application.UserProfileClient;
import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ActiveProfiles("test")
@SpringBootTest(classes = CalendarServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalendarPostgresIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserProfileClient userProfileClient;
    private UUID createdEventId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        var database = IntegrationTestDatabaseUrl.fromApplicationYaml("calendar_service");
        properties.add("spring.datasource.url", database::url);
        properties.add("spring.datasource.username", database::username);
        properties.add("spring.datasource.password", database::password);
    }

    @Test
    void flywayCreatesSchemaAndCalendarEventPersistsCanBeRetrievedAndCancelled() {
        Integer applied = jdbc.queryForObject("SELECT count(*) FROM calendar_service.flyway_schema_history "
                + "WHERE version = '1' AND success = true", Integer.class);
        assertEquals(1, applied);

        String email = "calendar-" + UUID.randomUUID() + "@example.test";
        when(userProfileClient.get(email)).thenReturn(new UserProfileClient.UserProfileResponse(
                UUID.randomUUID(), email, "Calendar Integration", "Asia/Kolkata", List.of(), Instant.now(), Instant.now()));
        Instant start = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant end = start.plusSeconds(1800);
        var request = new CalendarDtos.CreateCalendarEventRequest(email, "Postgres event", start, end);

        var created = http.postForEntity(url("/api/calendar/events"), request, CalendarDtos.CalendarEventResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        createdEventId = created.getBody().id();

        var retrieved = http.getForEntity(url("/api/calendar/events/{eventId}"),
                CalendarDtos.CalendarEventResponse.class, created.getBody().id());
        assertEquals(HttpStatus.OK, retrieved.getStatusCode());
        assertEquals(start, retrieved.getBody().startAt());
        assertEquals(end, retrieved.getBody().endAt());

        var events = http.getForEntity(url("/api/calendar/users/{email}/events"), CalendarDtos.CalendarEventResponse[].class, email);
        assertEquals(1, events.getBody().length);
        assertEquals(created.getBody().id(), events.getBody()[0].id());

        http.delete(url("/api/calendar/events/{eventId}"), created.getBody().id());
        var cancelled = http.getForEntity(url("/api/calendar/events/{eventId}"),
                CalendarDtos.CalendarEventResponse.class, created.getBody().id());
        assertEquals(CalendarEventStatus.CANCELLED, cancelled.getBody().status());
    }

    @Test
    void userServiceFailureDoesNotPersistCalendarEvent() {
        String email = "unavailable-" + UUID.randomUUID() + "@example.test";
        String title = "Unpersisted event " + UUID.randomUUID();
        doThrow(new ServiceUnavailableException("User Service request failed", null))
                .when(userProfileClient).get(email);
        Instant start = Instant.now().plusSeconds(3600);
        var request = new CalendarDtos.CreateCalendarEventRequest(email, title, start, start.plusSeconds(1800));

        var response = http.postForEntity(url("/api/calendar/events"), request, String.class);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM calendar_service.calendar_events WHERE title = ?",
                Integer.class, title));
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    @org.junit.jupiter.api.AfterEach
    void removeFixture() {
        if (createdEventId != null) {
            jdbc.update("DELETE FROM calendar_service.calendar_events WHERE id = ?", createdEventId);
            createdEventId = null;
        }
    }
}
