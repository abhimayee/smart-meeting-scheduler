package com.example.scheduler.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Runs the real HTTP service chain against the dedicated PostgreSQL integration database. */
class AiMeetingOrchestrationHttpIntegrationTest {
    private static final InProcessServiceStack SERVICES = new InProcessServiceStack();
    private static final TestRestTemplate HTTP = new TestRestTemplate();
    private static final List<String> CREATED_USERS = new ArrayList<>();
    private static final List<String> CREATED_MEETINGS = new ArrayList<>();
    private static final List<String> CREATED_EVENTS = new ArrayList<>();

    @BeforeAll
    static void startServices() {
        SERVICES.startBackends();
        SERVICES.startAi();
    }

    @AfterEach
    void removeFixtures() {
        for (String id : CREATED_MEETINGS) {
            SERVICES.meetingJdbc().update("DELETE FROM meeting_service.meeting_participants WHERE meeting_id = ?",
                    UUID.fromString(id));
            SERVICES.meetingJdbc().update("DELETE FROM meeting_service.meetings WHERE id = ?", UUID.fromString(id));
        }
        for (String id : CREATED_EVENTS) {
            SERVICES.calendarJdbc().update("DELETE FROM calendar_service.calendar_events WHERE id = ?", UUID.fromString(id));
        }
        for (String email : CREATED_USERS) {
            SERVICES.calendarJdbc().update("DELETE FROM calendar_service.calendar_events WHERE user_email = ?", email);
            SERVICES.userJdbc().update("DELETE FROM user_service.user_working_hours WHERE user_id IN "
                    + "(SELECT id FROM user_service.users WHERE email = ?)", email);
            SERVICES.userJdbc().update("DELETE FROM user_service.users WHERE email = ?", email);
        }
        CREATED_MEETINGS.clear();
        CREATED_EVENTS.clear();
        CREATED_USERS.clear();
    }

    @AfterAll
    static void stopServices() {
        SERVICES.close();
    }

    @Test
    void clarificationDoesNotCreateMeetingOrCallDownstreamServices() {
        int before = meetingCount();
        var result = parseThenOrchestrate("title: Clarification Test\norganizerEmail: owner@example.test");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("status", "NEEDS_CLARIFICATION");
        assertThat(result.getBody()).containsKey("clarification");
        assertThat(result.getBody().get("meetingId")).isNull();
        assertThat(meetingCount()).isEqualTo(before);
    }

    @Test
    void validPromptCreatesMeetingFetchesAvailabilityAndReturnsSuggestionsWithoutConfirming() {
        String organizer = createUser("ai-organizer-" + UUID.randomUUID() + "@example.test", "Asia/Kolkata", true);
        String required = createUser("ai-required-" + UUID.randomUUID() + "@example.test", "Europe/London", true);
        String optional = createUser("ai-optional-" + UUID.randomUUID() + "@example.test", "America/New_York", true);
        Instant busyStart = createBusyEvent(required);

        var parsed = parse(prompt(organizer, required, optional));
        assertThat(parsed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parsed.getBody()).containsEntry("status", "COMPLETE");
        var result = HTTP.postForEntity(aiUrl("/api/ai/meetings/orchestrate"), parsed.getBody(), Map.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("status", "SUGGESTIONS_RETURNED");
        String meetingId = (String) result.getBody().get("meetingId");
        assertThat(meetingId).isNotBlank();
        CREATED_MEETINGS.add(meetingId);
        var suggestions = (List<Map<String, Object>>) result.getBody().get("suggestions");
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.getFirst()).containsKeys("start", "end", "participants");
        Instant busyEnd = busyStart.plusSeconds(1800);
        assertThat(suggestions).allSatisfy(suggestion -> {
            Instant suggestedStart = Instant.parse((String) suggestion.get("start"));
            Instant suggestedEnd = Instant.parse((String) suggestion.get("end"));
            assertThat(suggestedStart.isBefore(busyEnd) && suggestedEnd.isAfter(busyStart)).isFalse();
        });
        var slotParticipants = (List<Map<String, Object>>) suggestions.getFirst().get("participants");
        assertThat(slotParticipants).extracting(participant -> participant.get("email"))
                .contains(organizer, required, optional);
        assertThat(slotParticipants).allSatisfy(participant -> {
            String localStart = (String) participant.get("localStart");
            assertThat(localStart).isNotNull();
            assertThat(OffsetDateTime.parse(localStart).toInstant())
                    .isEqualTo(Instant.parse((String) suggestions.getFirst().get("start")));
        });

        var meeting = HTTP.getForEntity(meetingUrl("/api/meetings/{meetingId}"), Map.class, meetingId);
        assertThat(meeting.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meeting.getBody()).containsEntry("status", "REQUESTED");
        assertThat(meeting.getBody().get("confirmedStart")).isNull();
        assertThat(meeting.getBody().get("confirmedEnd")).isNull();

        var selectedSlot = suggestions.getFirst();
        var confirmation = Map.of("start", selectedSlot.get("start"), "end", selectedSlot.get("end"));
        var confirmed = HTTP.postForEntity(meetingUrl("/api/meetings/{meetingId}/confirm"), confirmation,
                Map.class, meetingId);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody()).containsEntry("status", "CONFIRMED");
        assertThat(confirmed.getBody().get("confirmedStart")).isEqualTo(selectedSlot.get("start"));
        assertThat(confirmed.getBody().get("confirmedEnd")).isEqualTo(selectedSlot.get("end"));

        var cancelled = HTTP.postForEntity(meetingUrl("/api/meetings/{meetingId}/cancel"), null,
                Map.class, meetingId);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody()).containsEntry("status", "CANCELLED");
    }

    @Test
    void returnsMeetingIdAndEmptySuggestionsWhenNoAvailabilityExists() {
        String organizer = createUser("ai-no-hours-" + UUID.randomUUID() + "@example.test", "Asia/Kolkata", false);
        String required = createUser("ai-no-hours-required-" + UUID.randomUUID() + "@example.test", "Europe/London", true);

        var parsed = parse(prompt(organizer, required, null));
        var result = HTTP.postForEntity(aiUrl("/api/ai/meetings/orchestrate"), parsed.getBody(), Map.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("status", "SUGGESTIONS_RETURNED");
        assertThat(result.getBody().get("meetingId")).isNotNull();
        CREATED_MEETINGS.add((String) result.getBody().get("meetingId"));
        assertThat(result.getBody().get("suggestions")).asList().isEmpty();
    }

    @Test
    void reportsDownstreamUserFailureWithoutFalseSuccess() {
        String missingOrganizer = "missing-" + UUID.randomUUID() + "@example.test";
        String attendee = createUser("ai-attendee-" + UUID.randomUUID() + "@example.test", "America/New_York", true);
        var parsed = parse(prompt(missingOrganizer, attendee, null));
        var result = HTTP.postForEntity(aiUrl("/api/ai/meetings/orchestrate"), parsed.getBody(), Map.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.getBody()).containsEntry("message", "A required service is temporarily unavailable");
        assertThat(result.getBody().get("meetingId")).isNull();
    }

    private String createUser(String email, String timeZone, boolean withWorkingHours) {
        CREATED_USERS.add(email);
        var body = new HashMap<String, Object>();
        body.put("email", email);
        body.put("displayName", "AI integration attendee");
        body.put("timeZone", timeZone);
        if (withWorkingHours) body.put("workingHours", workingHours());
        ResponseEntity<Map> response = HTTP.postForEntity(SERVICES.userUrl() + "/api/users", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return email;
    }

    private List<Map<String, String>> workingHours() {
        var hours = new ArrayList<Map<String, String>>();
        for (DayOfWeek day : DayOfWeek.values()) {
            hours.add(Map.of("dayOfWeek", day.name(), "startTime", "09:00", "endTime", "17:00"));
        }
        return hours;
    }

    private Instant createBusyEvent(String email) {
        LocalDate meetingDay = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
        var start = meetingDay.atTime(LocalTime.of(9, 30)).atZone(ZoneId.of("Europe/London")).toInstant();
        var body = Map.of("userEmail", email, "title", "E2E busy fixture", "startAt", start.toString(),
                "endAt", start.plusSeconds(1800).toString());
        ResponseEntity<Map> response = HTTP.postForEntity(SERVICES.calendarUrl() + "/api/calendar/events", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CREATED_EVENTS.add((String) response.getBody().get("id"));
        return start;
    }

    private String prompt(String organizer, String required, String optional) {
        LocalDate preferredDay = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);
        String prompt = "title: AI orchestration e2e " + UUID.randomUUID() + "\n"
                + "agenda: Verify AI workflow | Confirm no auto-booking\n"
                + "durationMinutes: 30\norganizerEmail: " + organizer + "\nrequiredParticipants: " + required + "\n"
                + "preferredStartDate: " + preferredDay + "\npreferredEndDate: " + preferredDay + "\n"
                + "preferredStartTime: 14:00\npreferredTimeZone: Asia/Kolkata\n";
        return optional == null ? prompt : prompt + "optionalParticipants: " + optional + "\n";
    }

    private ResponseEntity<Map> parse(String prompt) {
        return HTTP.postForEntity(aiUrl("/api/ai/meetings/parse"), Map.of("prompt", prompt), Map.class);
    }

    private ResponseEntity<Map> parseThenOrchestrate(String prompt) {
        ResponseEntity<Map> parsed = parse(prompt);
        assertThat(parsed.getStatusCode()).isEqualTo(HttpStatus.OK);
        return HTTP.postForEntity(aiUrl("/api/ai/meetings/orchestrate"), parsed.getBody(), Map.class);
    }

    private int meetingCount() {
        return SERVICES.meetingJdbc().queryForObject("SELECT count(*) FROM meeting_service.meetings", Integer.class);
    }

    private String aiUrl(String path) { return "http://127.0.0.1:" + SERVICES.aiPort() + path; }
    private String meetingUrl(String path) { return SERVICES.meetingUrl() + path; }
}
