package com.example.scheduler.meeting.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.example.scheduler.MeetingServiceApplication;
import com.example.scheduler.meeting.api.MeetingDtos;
import com.example.scheduler.meeting.application.SchedulingClient;
import com.example.scheduler.meeting.application.UserDirectoryClient;
import com.example.scheduler.meeting.domain.MeetingStatus;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Exercises the Meeting HTTP API and PostgreSQL without requiring separately started services. */
@ActiveProfiles("test")
@SpringBootTest(classes = MeetingServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeetingServiceHttpIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserDirectoryClient userDirectoryClient;
    @MockitoBean SchedulingClient schedulingClient;
    private UUID fixtureMeetingId;

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry properties) {
        var database = com.example.scheduler.test.support.IntegrationTestDatabaseUrl
                .fromApplicationYaml("meeting_service");
        properties.add("spring.datasource.url", database::url);
        properties.add("spring.datasource.username", database::username);
        properties.add("spring.datasource.password", database::password);
    }

    @Test
    void meetingApiCreatesRetrievesSchedulesConfirmsAndCancelsPersistedMeeting() {
        var organizer = "meeting-http-owner-" + UUID.randomUUID() + "@example.test";
        var attendee = "meeting-http-attendee-" + UUID.randomUUID() + "@example.test";
        when(userDirectoryClient.timeZoneFor(organizer)).thenReturn(ZoneId.of("Asia/Kolkata"));
        var start = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        var slot = new MeetingDtos.SlotSuggestionResponse(start, start.plusSeconds(1800), true, 0, 0, List.of());
        when(schedulingClient.findSlots(any())).thenReturn(List.of(slot));

        var create = new MeetingDtos.CreateMeetingRequest("Meeting HTTP integration " + UUID.randomUUID(),
                "Create and exercise the persisted meeting lifecycle", 30, organizer, List.of(attendee), List.of());
        var created = http.postForEntity(url("/api/meetings"), create, MeetingDtos.MeetingResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        fixtureMeetingId = created.getBody().id();

        var retrieved = http.getForEntity(url("/api/meetings/{id}"), MeetingDtos.MeetingResponse.class, fixtureMeetingId);
        assertEquals(HttpStatus.OK, retrieved.getStatusCode());
        assertEquals(MeetingStatus.REQUESTED, retrieved.getBody().status());
        assertEquals(attendee, retrieved.getBody().participants().getFirst().email());

        var schedule = http.postForEntity(url("/api/meetings/{id}/schedule"), null,
                MeetingDtos.ScheduleMeetingResponse.class, fixtureMeetingId);
        assertEquals(HttpStatus.OK, schedule.getStatusCode());
        assertEquals(List.of(slot), schedule.getBody().suggestions());

        var confirmation = new MeetingDtos.ConfirmMeetingRequest(slot.start(), slot.end());
        var confirmed = http.postForEntity(url("/api/meetings/{id}/confirm"), confirmation,
                MeetingDtos.MeetingResponse.class, fixtureMeetingId);
        assertEquals(HttpStatus.OK, confirmed.getStatusCode());
        assertEquals(MeetingStatus.CONFIRMED, confirmed.getBody().status());
        assertEquals(slot.start(), confirmed.getBody().confirmedStart());
        assertEquals(slot.end(), confirmed.getBody().confirmedEnd());

        var cancelled = http.postForEntity(url("/api/meetings/{id}/cancel"), null,
                MeetingDtos.MeetingResponse.class, fixtureMeetingId);
        assertEquals(HttpStatus.OK, cancelled.getStatusCode());
        assertEquals(MeetingStatus.CANCELLED, cancelled.getBody().status());
    }

    @Test
    void mapsUserNotFoundWithoutPersistingMeeting() {
        String missingOrganizer = "missing-meeting-user-" + UUID.randomUUID() + "@example.test";
        doAnswer(invocation -> {
            if (missingOrganizer.equals(invocation.getArgument(0)))
                throw new ResourceNotFoundException("User was not found");
            return null;
        }).when(userDirectoryClient).requireExisting(anyString());
        String title = "Missing user meeting " + UUID.randomUUID();
        var request = new MeetingDtos.CreateMeetingRequest(title, "Dependency error", 30,
                missingOrganizer, List.of(), List.of());

        var response = http.postForEntity(url("/api/meetings"), request, String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM meeting_service.meetings WHERE organizer_email = ?",
                Integer.class, missingOrganizer));
    }

    @AfterEach
    void removeFixture() {
        if (fixtureMeetingId != null) {
            jdbc.update("DELETE FROM meeting_service.meeting_participants WHERE meeting_id = ?", fixtureMeetingId);
            jdbc.update("DELETE FROM meeting_service.meetings WHERE id = ?", fixtureMeetingId);
            fixtureMeetingId = null;
        }
    }

    private String url(String path) { return "http://localhost:" + port + path; }
}
