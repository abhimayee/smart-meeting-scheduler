package com.example.scheduler.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.scheduler.ai.domain.MeetingRequestDetails;
import com.example.scheduler.ai.domain.MeetingSchedulePreferences;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MeetingServiceHttpClientTest {
    @Test
    void usesExistingMeetingCreateAndScheduleContracts() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var meetingId = UUID.randomUUID();
        server.expect(requestTo("http://meeting/api/meetings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"title":"Architecture Review","agenda":"Boundaries; API design","durationMinutes":45,
                         "organizerEmail":"owner@example.com","requiredParticipants":["john@example.com"],
                         "optionalParticipants":["sarah@example.com"]}
                        """))
                .andRespond(withSuccess("{\"id\":\"%s\"}".formatted(meetingId), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://meeting/api/meetings/" + meetingId + "/schedule"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"meetingId":"%s","suggestions":[{"start":"2026-10-12T10:00:00Z",
                         "end":"2026-10-12T10:45:00Z","requiredParticipantsAvailable":true,
                         "optionalParticipantsAvailable":1,"optionalParticipantsTotal":1,
                         "participants":[{"email":"owner@example.com","type":"REQUIRED","available":true,
                         "localStart":"2026-10-12T15:30:00+05:30[Asia/Kolkata]",
                         "localEnd":"2026-10-12T16:15:00+05:30[Asia/Kolkata]"}]}]}
                        """.formatted(meetingId), MediaType.APPLICATION_JSON));
        var client = new MeetingServiceHttpClient(builder, "http://meeting");

        var createdId = client.createMeeting(details());
        var suggestions = client.requestSchedulingSuggestions(createdId);

        assertThat(createdId).isEqualTo(meetingId);
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.getFirst().participants().getFirst().localStart())
                .isEqualTo("2026-10-12T15:30:00+05:30[Asia/Kolkata]");
        server.verify();
    }

    @Test
    void mapsMeetingServiceHttpErrorToUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://meeting/api/meetings"))
                .andRespond(withServerError());
        var client = new MeetingServiceHttpClient(builder, "http://meeting");

        assertThatThrownBy(() -> client.createMeeting(details())).isInstanceOf(ServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void mapsMeetingServiceTimeoutToUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var meetingId = UUID.randomUUID();
        server.expect(requestTo("http://meeting/api/meetings/" + meetingId + "/schedule"))
                .andRespond(withException(new IOException("Read timed out")));
        var client = new MeetingServiceHttpClient(builder, "http://meeting");

        assertThatThrownBy(() -> client.requestSchedulingSuggestions(meetingId))
                .isInstanceOf(ServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void sendsOptionalPreferencesToMeetingScheduleApi() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var meetingId = UUID.randomUUID();
        server.expect(requestTo("http://meeting/api/meetings/" + meetingId + "/schedule"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                         {"preferredStartDate":"2026-10-12","preferredEndDate":"2026-10-14","preferredStartTime":"10:00","preferredTimeZone":"Europe/London"}
                        """))
                .andRespond(withSuccess("{\"meetingId\":\"%s\",\"suggestions\":[]}".formatted(meetingId),
                        MediaType.APPLICATION_JSON));
        var client = new MeetingServiceHttpClient(builder, "http://meeting");

        var suggestions = client.requestSchedulingSuggestions(meetingId,
                new MeetingSchedulePreferences(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-14"),
                        LocalTime.of(10, 0), java.time.ZoneId.of("Europe/London")));

        assertThat(suggestions).isEmpty();
        server.verify();
    }

    private static MeetingRequestDetails details() {
        return new MeetingRequestDetails("Architecture Review", null, List.of("Boundaries", "API design"), 45,
                "owner@example.com", List.of("john@example.com"), List.of("sarah@example.com"),
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalTime.of(10, 0));
    }
}
