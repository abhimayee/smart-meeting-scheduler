package com.example.scheduler.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.scheduler.ai.domain.MeetingAiResult;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class OpenAiMeetingAiServiceTest {
    private static final String BASE_URL = "https://provider.example/v1";
    private static final String MODEL = "test-model";
    private static final String API_KEY = "test-only-key";

    @Test
    void parsesAndNormalizesValidStructuredOutput() {
        var fixture = client();
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.durationMinutes.type")
                        .value("integer"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.durationMinutes.minimum")
                        .value(1))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.agenda.minItems").value(1))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.requiredParticipants.minItems")
                        .value(1))
                .andRespond(withSuccess(completion(validOutput()), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("Schedule an API review next Wednesday");

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.COMPLETE);
        assertThat(result.meetingRequest().organizerEmail()).isEqualTo("owner@example.com");
        assertThat(result.meetingRequest().requiredParticipants()).containsExactly("john@example.com");
        assertThat(result.meetingRequest().optionalParticipants()).containsExactly("sarah@example.com");
        assertThat(result.meetingRequest().preferredStartDate().toString()).isEqualTo("2026-10-14");
        assertThat(result.meetingRequest().preferredEndDate().toString()).isEqualTo("2026-10-14");
        assertThat(result.meetingRequest().preferredTimeZone()).isEqualTo(java.time.ZoneId.of("America/New_York"));
        fixture.server().verify();
    }

    @Test
    void defaultsMissingEndDateToStartDateForSingleDayPreference() {
        var fixture = client();
        String providerOutput = validOutput().replace("\"preferredEndDate\":\"2026-10-14\"",
                "\"preferredEndDate\":null");
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("Schedule an API review on October 14");

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.COMPLETE);
        assertThat(result.meetingRequest().preferredStartDate().toString()).isEqualTo("2026-10-14");
        assertThat(result.meetingRequest().preferredEndDate()).isEqualTo(result.meetingRequest().preferredStartDate());
        fixture.server().verify();
    }

    @Test
    void endDateWithoutStartDateStillRequiresClarification() {
        var fixture = client();
        String providerOutput = validOutput().replace("\"preferredStartDate\":\"2026-10-14\"",
                "\"preferredStartDate\":null");
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        assertClarification(fixture.service().parseMeetingRequest("Schedule an API review until October 14"));
        fixture.server().verify();
    }

    @Test
    void supportsOpenAiCompatibleLocalProviderWithoutAnApiKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var service = new OpenAiMeetingAiService(builder, new ObjectMapper(),
                "http://localhost:11434/v1", "", MODEL);
        server.expect(requestTo("http://localhost:11434/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(completion(validOutput()), MediaType.APPLICATION_JSON));

        assertThat(service.parseMeetingRequest("Schedule a meeting" ).status())
                .isEqualTo(MeetingAiResult.Status.COMPLETE);
        server.verify();
    }

    @Test
    void parsesOllamaMarkdownFencedJsonAndMapsItsPreferenceFields() {
        var fixture = client();
        String fencedJson = """
                ```json
                {
                  "title": "Phase 7 AI Smoke Test",
                  "organizerEmail": "phase7-test@example.com",
                  "participantEmails": ["participant@example.com"],
                  "durationMinutes": 30,
                  "preferredDate": "2026-10-19",
                  "preferredStartTime": "14:00",
                  "preferredTimeZone": "Europe/London",
                  "agenda": "Verify AI meeting orchestration"
                }
                ```
                """;
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content").value(Matchers.containsString(
                        "do not require or ask for the organizer's time zone when it is present")))
                .andRespond(withSuccess(completion(fencedJson), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("""
                Schedule a 30-minute meeting titled "Phase 7 AI Smoke Test".
                Organizer: phase7-test@example.com.
                Required participant: participant@example.com.
                Agenda: Verify AI meeting orchestration.
                Prefer October 19, 2026, at 2:00 PM Europe/London time.
                """);

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.COMPLETE);
        assertThat(result.meetingRequest().title()).isEqualTo("Phase 7 AI Smoke Test");
        assertThat(result.meetingRequest().organizerEmail()).isEqualTo("phase7-test@example.com");
        assertThat(result.meetingRequest().requiredParticipants()).containsExactly("participant@example.com");
        assertThat(result.meetingRequest().optionalParticipants()).isEmpty();
        assertThat(result.meetingRequest().durationMinutes()).isEqualTo(30);
        assertThat(result.meetingRequest().agenda()).containsExactly("Verify AI meeting orchestration");
        assertThat(result.meetingRequest().preferredStartDate().toString()).isEqualTo("2026-10-19");
        assertThat(result.meetingRequest().preferredEndDate()).isEqualTo(result.meetingRequest().preferredStartDate());
        assertThat(result.meetingRequest().preferredStartTime().toString()).isEqualTo("14:00");
        assertThat(result.meetingRequest().preferredTimeZone()).isEqualTo(java.time.ZoneId.of("Europe/London"));
        fixture.server().verify();
    }

    @Test
    void acceptsCompleteAsProviderSuccessStatus() {
        var fixture = client();
        String providerOutput = validOutput().replace("\"status\":\"PARSED\"", "\"status\":\"COMPLETE\"");
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("Schedule an API review");

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.COMPLETE);
        assertThat(result.meetingRequest().title()).isEqualTo("API Review");
        fixture.server().verify();
    }

    @Test
    void validFieldsOverrideContradictoryProviderClarificationStatus() {
        var fixture = client();
        String providerOutput = validOutput().replace("\"status\":\"PARSED\",\"clarification\":null",
                "\"status\":\"NEEDS_CLARIFICATION\",\"clarification\":\"All required details are present and unambiguous; no clarification needed. The extracted meeting request is provided below.\"");
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("valid meeting request");

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.COMPLETE);
        assertThat(result.meetingRequest().title()).isEqualTo("API Review");
        assertThat(result.meetingRequest().durationMinutes()).isEqualTo(45);
        fixture.server().verify();
    }

    @Test
    void preservesProviderClarificationWhenRequiredFieldsAreMissing() {
        var fixture = client();
        String providerOutput = validOutput()
                .replace("\"status\":\"PARSED\",\"clarification\":null",
                        "\"status\":\"NEEDS_CLARIFICATION\",\"clarification\":\"Please provide at least one agenda item.\"")
                .replace("\"agenda\":[\"API design\"]", "\"agenda\":[]");
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("incomplete meeting request");

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.NEEDS_CLARIFICATION);
        assertThat(result.meetingRequest()).isNull();
        assertThat(result.clarification()).isEqualTo("Please provide at least one agenda item.");
        fixture.server().verify();
    }

    @Test
    void completeProviderStatusDoesNotBypassInvalidFieldValidation() {
        var fixture = client();
        String providerOutput = validOutput().replace("\"status\":\"PARSED\"", "\"status\":\"COMPLETE\"")
                .replace("\"agenda\":[\"API design\"]", "\"agenda\":[]");
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("invalid meeting request");

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.NEEDS_CLARIFICATION);
        assertThat(result.meetingRequest()).isNull();
        fixture.server().verify();
    }

    @Test
    void explicitPreferenceZoneOverridesClarificationThatOrganizerZoneIsMissing() {
        var fixture = client();
        String providerOutput = """
                ```json
                {
                  "status": "NEEDS_CLARIFICATION",
                  "clarification": "The requested meeting date/time includes a time zone (Europe/London). However, no explicit organizer time zone is provided in the request.",
                  "title": "Phase 7 AI Smoke Test",
                  "description": null,
                  "agenda": ["Verify AI meeting orchestration"],
                  "durationMinutes": 30,
                  "organizerEmail": "phase7-test@example.com",
                  "requiredParticipants": ["participant@example.com"],
                  "optionalParticipants": [],
                  "preferredStartDate": "2026-10-19",
                  "preferredEndDate": "2026-10-19",
                  "preferredStartTime": "14:00",
                  "preferredTimeZone": "Europe/London"
                }
                ```
                """;
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("""
                Schedule a 30-minute meeting titled "Phase 7 AI Smoke Test".
                Organizer: phase7-test@example.com.
                Required participant: participant@example.com.
                Agenda: Verify AI meeting orchestration.
                Prefer October 19, 2026, at 2:00 PM Europe/London time.
                """);

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.COMPLETE);
        assertThat(result.meetingRequest().title()).isEqualTo("Phase 7 AI Smoke Test");
        assertThat(result.meetingRequest().organizerEmail()).isEqualTo("phase7-test@example.com");
        assertThat(result.meetingRequest().requiredParticipants()).containsExactly("participant@example.com");
        assertThat(result.meetingRequest().durationMinutes()).isEqualTo(30);
        assertThat(result.meetingRequest().agenda()).containsExactly("Verify AI meeting orchestration");
        assertThat(result.meetingRequest().preferredStartDate().toString()).isEqualTo("2026-10-19");
        assertThat(result.meetingRequest().preferredStartTime().toString()).isEqualTo("14:00");
        assertThat(result.meetingRequest().preferredTimeZone()).isEqualTo(java.time.ZoneId.of("Europe/London"));
        fixture.server().verify();
    }

    @Test
    void explicitPreferenceZoneDoesNotHideOtherProviderClarifications() {
        var fixture = client();
        String providerOutput = validOutput().replace("America/New_York", "Europe/London")
                .replace("\"status\":\"PARSED\",\"clarification\":null",
                        "\"status\":\"NEEDS_CLARIFICATION\",\"clarification\":\"The organizer time zone is not provided, and the agenda is ambiguous.\"")
                .replace("\"agenda\":[\"API design\"]", "\"agenda\":[]");
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(providerOutput), MediaType.APPLICATION_JSON));

        var result = fixture.service().parseMeetingRequest("Prefer October 19 at 2 PM Europe/London time");

        assertClarification(result);
        assertThat(result.clarification()).contains("agenda is ambiguous");
        fixture.server().verify();
    }

    @Test
    void malformedProviderJsonReturnsClarification() {
        var fixture = client();
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion("not-json"), MediaType.APPLICATION_JSON));

        assertClarification(fixture.service().parseMeetingRequest("prompt"));
    }

    @Test
    void missingRequiredFieldsReturnsClarification() {
        var fixture = client();
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion("{\"status\":\"PARSED\",\"title\":null,\"agenda\":[],\"durationMinutes\":null,\"organizerEmail\":null,\"requiredParticipants\":[],\"optionalParticipants\":[],\"preferredStartDate\":null,\"preferredEndDate\":null,\"preferredStartTime\":null,\"description\":null,\"clarification\":null}"),
                        MediaType.APPLICATION_JSON));

        assertClarification(fixture.service().parseMeetingRequest("incomplete prompt"));
    }

    @Test
    void incompleteAgendaDurationOrRequiredParticipantsCannotBeComplete() {
        var emptyAgenda = client();
        emptyAgenda.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(validOutput().replace("\"agenda\":[\"API design\"]", "\"agenda\":[]")),
                        MediaType.APPLICATION_JSON));
        assertClarification(emptyAgenda.service().parseMeetingRequest("valid prompt"));

        var nullDuration = client();
        nullDuration.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(validOutput().replace("\"durationMinutes\":45", "\"durationMinutes\":null")),
                        MediaType.APPLICATION_JSON));
        assertClarification(nullDuration.service().parseMeetingRequest("valid prompt"));

        var nonPositiveDuration = client();
        nonPositiveDuration.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(validOutput().replace("\"durationMinutes\":45", "\"durationMinutes\":0")),
                        MediaType.APPLICATION_JSON));
        assertClarification(nonPositiveDuration.service().parseMeetingRequest("valid prompt"));

        var emptyRequiredParticipants = client();
        emptyRequiredParticipants.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(validOutput().replace(
                        "\"requiredParticipants\":[\"John@Example.com\"]", "\"requiredParticipants\":[]")),
                        MediaType.APPLICATION_JSON));
        assertClarification(emptyRequiredParticipants.service().parseMeetingRequest("valid prompt"));
    }

    @Test
    void providerHttpFailureDoesNotFallBackToMock() {
        var fixture = client();
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        assertThatThrownBy(() -> fixture.service().parseMeetingRequest("title: this mock syntax is not used"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("AI provider request failed");
    }

    @Test
    void providerTimeoutDoesNotFallBackToMock() {
        var fixture = client();
        fixture.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(request -> { throw new ResourceAccessException("Read timed out"); });

        assertThatThrownBy(() -> fixture.service().parseMeetingRequest("title: deterministic mock prompt"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("AI provider request failed");
    }

    @Test
    void explicitClarificationIsReturnedAndInvalidParsedDataIsRejected() {
        var clarification = client();
        clarification.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion("{\"status\":\"NEEDS_CLARIFICATION\",\"clarification\":\"Which organizer?\",\"title\":null,\"description\":null,\"agenda\":[],\"durationMinutes\":null,\"organizerEmail\":null,\"requiredParticipants\":[],\"optionalParticipants\":[],\"preferredStartDate\":null,\"preferredEndDate\":null,\"preferredStartTime\":null}"),
                        MediaType.APPLICATION_JSON));
        assertThat(clarification.service().parseMeetingRequest("ambiguous").clarification())
                .isEqualTo("Which organizer?");

        var invalid = client();
        invalid.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(validOutput().replace("John@Example.com", "Owner@Example.com")),
                        MediaType.APPLICATION_JSON));
        assertClarification(invalid.service().parseMeetingRequest("prompt"));

        var invalidZone = client();
        invalidZone.server().expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(completion(validOutput().replace("America/New_York", "Not/A_Zone")),
                        MediaType.APPLICATION_JSON));
        assertClarification(invalidZone.service().parseMeetingRequest("prompt"));
    }

    private static Fixture client() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var service = new OpenAiMeetingAiService(builder, new ObjectMapper(), BASE_URL, API_KEY, MODEL);
        return new Fixture(service, server);
    }

    private static String validOutput() {
        return "{\"status\":\"PARSED\",\"clarification\":null,\"title\":\"API Review\",\"description\":null,\"agenda\":[\"API design\"],\"durationMinutes\":45,\"organizerEmail\":\"Owner@Example.com\",\"requiredParticipants\":[\"John@Example.com\"],\"optionalParticipants\":[\"Sarah@Example.com\"],\"preferredStartDate\":\"2026-10-14\",\"preferredEndDate\":\"2026-10-14\",\"preferredStartTime\":\"10:30\",\"preferredTimeZone\":\"America/New_York\"}";
    }

    private static String completion(String content) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of("choices", List.of(java.util.Map.of(
                    "message", java.util.Map.of("content", content)))));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private static void assertClarification(MeetingAiResult result) {
        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.NEEDS_CLARIFICATION);
        assertThat(result.meetingRequest()).isNull();
        assertThat(result.clarification()).isNotBlank();
    }

    private record Fixture(OpenAiMeetingAiService service, MockRestServiceServer server) { }
}
