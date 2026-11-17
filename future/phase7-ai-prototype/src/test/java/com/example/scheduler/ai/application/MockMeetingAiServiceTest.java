package com.example.scheduler.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.scheduler.ai.domain.MeetingAiResult;
import org.junit.jupiter.api.Test;

class MockMeetingAiServiceTest {
    private final MockMeetingAiService service = new MockMeetingAiService();

    @Test
    void rejectsBlankPrompt() {
        assertThatThrownBy(() -> service.parseMeetingRequest("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractsAndNormalizesSupportedKeyValuePrompt() {
        var result = service.parseMeetingRequest("""
                title:  API Design Review
                agenda: API design | Database optimization
                durationMinutes: 45
                organizerEmail: Abhipsa@Example.COM
                requiredParticipants: JOHN@example.com
                optionalParticipants: Sarah@Example.com
                preferredStartDate: 2026-10-07
                preferredEndDate: 2026-10-07
                preferredStartTime: 10:30
                """);

        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.COMPLETE);
        assertThat(result.meetingRequest().title()).isEqualTo("API Design Review");
        assertThat(result.meetingRequest().agenda()).containsExactly("API design", "Database optimization");
        assertThat(result.meetingRequest().durationMinutes()).isEqualTo(45);
        assertThat(result.meetingRequest().organizerEmail()).isEqualTo("abhipsa@example.com");
        assertThat(result.meetingRequest().requiredParticipants()).containsExactly("john@example.com");
        assertThat(result.meetingRequest().optionalParticipants()).containsExactly("sarah@example.com");
        assertThat(result.meetingRequest().preferredStartDate().toString()).isEqualTo("2026-10-07");
        assertThat(result.meetingRequest().preferredStartTime().toString()).isEqualTo("10:30");
    }

    @Test
    void extractsExplicitIanaPreferenceZoneAndLeavesItAbsentByDefault() {
        var explicit = service.parseMeetingRequest(validPrompt() + "preferredStartDate: 2026-10-07\n"
                + "preferredEndDate: 2026-10-07\npreferredStartTime: 10:30\n"
                + "preferredTimeZone: America/New_York\n");
        var defaultZone = service.parseMeetingRequest(validPrompt());

        assertThat(explicit.meetingRequest().preferredTimeZone()).isEqualTo(java.time.ZoneId.of("America/New_York"));
        assertThat(defaultZone.meetingRequest().preferredTimeZone()).isNull();
    }

    @Test
    void returnsClarificationForInvalidValues() {
        var invalidDuration = service.parseMeetingRequest(validPrompt().replace("durationMinutes: 45", "durationMinutes: 0"));
        var invalidEmail = service.parseMeetingRequest(validPrompt()
                .replace("organizerEmail: owner@example.com", "organizerEmail: not-an-email"));
        var invalidDate = service.parseMeetingRequest(validPrompt() + "preferredStartDate: 2026-99-99\n");
        var invalidTime = service.parseMeetingRequest(validPrompt() + "preferredStartTime: 25:00\n");
        var invalidZone = service.parseMeetingRequest(validPrompt() + "preferredTimeZone: Not/A_Zone\n");

        assertClarification(invalidDuration);
        assertClarification(invalidEmail);
        assertThat(invalidEmail.clarification()).contains("valid email");
        assertClarification(invalidDate);
        assertClarification(invalidTime);
        assertClarification(invalidZone);
    }

    @Test
    void returnsClarificationWhenRequiredDetailsAreMissing() {
        var result = service.parseMeetingRequest("title: Design Review\norganizerEmail: owner@example.com");

        assertClarification(result);
        assertThat(result.clarification()).contains("agenda", "durationMinutes");
    }

    @Test
    void returnsClarificationForAmbiguousDuplicateFields() {
        var result = service.parseMeetingRequest(validPrompt() + "title: A second title\n");

        assertClarification(result);
        assertThat(result.clarification()).contains("more than once");
    }

    @Test
    void returnsClarificationForDuplicateOrOrganizerParticipants() {
        var duplicate = service.parseMeetingRequest(validPrompt()
                .replace("requiredParticipants: john@example.com", "requiredParticipants: john@example.com, JOHN@example.com"));
        var organizerParticipant = service.parseMeetingRequest(validPrompt()
                .replace("requiredParticipants: john@example.com", "requiredParticipants: owner@example.com"));

        assertClarification(duplicate);
        assertClarification(organizerParticipant);
    }

    private static String validPrompt() {
        return "title: API Review\nagenda: API design\ndurationMinutes: 45\n"
                + "organizerEmail: owner@example.com\nrequiredParticipants: john@example.com\n";
    }

    private static void assertClarification(MeetingAiResult result) {
        assertThat(result.status()).isEqualTo(MeetingAiResult.Status.NEEDS_CLARIFICATION);
        assertThat(result.meetingRequest()).isNull();
        assertThat(result.clarification()).isNotBlank();
    }
}
