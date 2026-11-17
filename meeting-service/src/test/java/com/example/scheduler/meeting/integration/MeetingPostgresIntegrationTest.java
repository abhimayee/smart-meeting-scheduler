package com.example.scheduler.meeting.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;

import com.example.scheduler.MeetingServiceApplication;
import com.example.scheduler.test.support.IntegrationTestDatabaseUrl;
import com.example.scheduler.meeting.api.MeetingDtos;
import com.example.scheduler.meeting.application.SchedulingClient;
import com.example.scheduler.meeting.application.UserDirectoryClient;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(classes = MeetingServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MeetingPostgresIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserDirectoryClient userDirectoryClient;
    @MockitoBean SchedulingClient schedulingClient;
    private UUID createdMeetingId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        var database = IntegrationTestDatabaseUrl.fromApplicationYaml("meeting_service");
        properties.add("spring.datasource.url", database::url);
        properties.add("spring.datasource.username", database::username);
        properties.add("spring.datasource.password", database::password);
    }

    @Test
    void flywayCreatesSchemaAndMeetingAndParticipantsPersistAcrossHttpRequests() {
        Integer applied = jdbc.queryForObject("SELECT count(*) FROM meeting_service.flyway_schema_history "
                + "WHERE version = '1' AND success = true", Integer.class);
        assertEquals(1, applied);

        String organizer = "meeting-" + UUID.randomUUID() + "@example.test";
        String participant = "attendee-" + UUID.randomUUID() + "@example.test";
        String title = "Postgres meeting " + UUID.randomUUID();
        var create = new MeetingDtos.CreateMeetingRequest(title, "Persistence integration", 30,
                organizer, List.of(participant), List.of());
        var created = http.postForEntity(url("/api/meetings"), create, MeetingDtos.MeetingResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());
        createdMeetingId = created.getBody().id();
        assertEquals(1, created.getBody().participants().size());

        var retrieved = http.getForEntity(url("/api/meetings/{id}"), MeetingDtos.MeetingResponse.class,
                created.getBody().id());
        var participants = http.getForEntity(url("/api/meetings/{id}/participants"),
                MeetingDtos.ParticipantResponse[].class, created.getBody().id());
        assertEquals(HttpStatus.OK, retrieved.getStatusCode());
        assertEquals(created.getBody().id(), retrieved.getBody().id());
        assertEquals(title, retrieved.getBody().title());
        assertEquals(participant, participants.getBody()[0].email());
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM meeting_service.meeting_participants WHERE meeting_id = ?",
                Integer.class, created.getBody().id()) == 1);
    }

    @Test
    void failedParticipantInsertRollsBackTheMeetingTransaction() {
        String organizer = "rollback-owner-" + UUID.randomUUID() + "@example.test";
        String blockedParticipant = "rollback-attendee-" + UUID.randomUUID() + "@example.test";
        String title = "Rollback meeting " + UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String functionName = "it_fail_participant_" + suffix;
        String triggerName = "it_fail_participant_" + suffix;
        jdbc.execute(("CREATE FUNCTION meeting_service.%s() RETURNS trigger AS $test$ "
                + "BEGIN IF NEW.email = '%s' THEN "
                + "RAISE EXCEPTION 'forced integration constraint failure' USING ERRCODE = '23514'; "
                + "END IF; RETURN NEW; END; $test$ LANGUAGE plpgsql").formatted(functionName, blockedParticipant));
        jdbc.execute(("CREATE TRIGGER %s BEFORE INSERT ON meeting_service.meeting_participants "
                + "FOR EACH ROW EXECUTE FUNCTION meeting_service.%s()").formatted(triggerName, functionName));
        try {
            var create = new MeetingDtos.CreateMeetingRequest(title, "Rollback check", 30,
                    organizer, List.of(blockedParticipant), List.of());
            var response = http.postForEntity(url("/api/meetings"), create, String.class);
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            Integer meetingCount = jdbc.queryForObject("SELECT count(*) FROM meeting_service.meetings "
                    + "WHERE organizer_email = ? AND title = ?", Integer.class, organizer, title);
            assertEquals(0, meetingCount);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + triggerName + " ON meeting_service.meeting_participants");
            jdbc.execute("DROP FUNCTION IF EXISTS meeting_service." + functionName + "()");
        }
    }

    @Test
    void mapsUserDirectoryNotFoundAndPersistsNothing() {
        String missingEmail = "missing-" + UUID.randomUUID() + "@example.test";
        String title = "Missing user meeting " + UUID.randomUUID();
        doThrow(new ResourceNotFoundException("User was not found"))
                .when(userDirectoryClient).requireExisting(missingEmail);
        var request = new MeetingDtos.CreateMeetingRequest(title, "Dependency error check", 30,
                missingEmail, List.of(), List.of());

        var response = http.postForEntity(url("/api/meetings"), request, String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Integer meetingCount = jdbc.queryForObject("SELECT count(*) FROM meeting_service.meetings WHERE title = ?",
                Integer.class, title);
        assertEquals(0, meetingCount);
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    @org.junit.jupiter.api.AfterEach
    void removeFixture() {
        if (createdMeetingId != null) {
            jdbc.update("DELETE FROM meeting_service.meeting_participants WHERE meeting_id = ?", createdMeetingId);
            jdbc.update("DELETE FROM meeting_service.meetings WHERE id = ?", createdMeetingId);
            createdMeetingId = null;
        }
    }
}
