package com.example.scheduler.meeting.application;

import com.example.scheduler.meeting.api.MeetingDtos;
import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

class SchedulingServiceClientTest {
    @Test void postsMeetingParticipantsAndMapsSuggestionResponse() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://scheduling/api/scheduling/find-slots"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"organizer":"organizer@example.com","participants":[{"email":"bob@example.com","type":"REQUIRED"}],
                         "durationMinutes":30,"startDate":[2026,10,10],"endDate":[2026,10,24],
                         "preferredDate":null,"preferredStartTime":null}
                        """))
                .andRespond(withSuccess("""
                        {"suggestions":[{"start":"2026-10-12T10:00:00Z","end":"2026-10-12T10:30:00Z",
                         "requiredParticipantsAvailable":true,"optionalParticipantsAvailable":0,"optionalParticipantsTotal":0,
                         "participants":[{"email":"organizer@example.com","type":"REQUIRED","available":true,
                         "localStart":"2026-10-12T15:30:00+05:30[Asia/Kolkata]","localEnd":"2026-10-12T16:00:00+05:30[Asia/Kolkata]"}]}]}
                        """, MediaType.APPLICATION_JSON));
        var client = new SchedulingServiceClient(builder, "http://scheduling");
        var request = new MeetingDtos.FindSlotsRequest("organizer@example.com",
                List.of(new MeetingDtos.SchedulingParticipantRequest("bob@example.com", ParticipantType.REQUIRED)),
                30, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-24"), null, null);

        var suggestions = client.findSlots(request);

        assertEquals(1, suggestions.size());
        assertEquals(Instant.parse("2026-10-12T10:00:00Z"), suggestions.getFirst().start());
        assertEquals("2026-10-12T15:30:00+05:30[Asia/Kolkata]",
                suggestions.getFirst().participants().getFirst().localStart());
        server.verify();
    }

    @Test void mapsSchedulingServiceFailureToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://scheduling/api/scheduling/find-slots"))
                .andRespond(withServerError());
        var client = new SchedulingServiceClient(builder, "http://scheduling");
        var request = new MeetingDtos.FindSlotsRequest("organizer@example.com", List.of(), 30,
                LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-24"), null, null);

        assertThrows(ServiceUnavailableException.class, () -> client.findSlots(request));
        server.verify();
    }

    @Test void mapsSchedulingServiceReadTimeoutToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://scheduling/api/scheduling/find-slots"))
                .andRespond(withException(new IOException("Read timed out")));
        var client = new SchedulingServiceClient(builder, "http://scheduling");
        var request = new MeetingDtos.FindSlotsRequest("organizer@example.com", List.of(), 30,
                LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-24"), null, null);

        assertThrows(ServiceUnavailableException.class, () -> client.findSlots(request));
        server.verify();
    }
}
