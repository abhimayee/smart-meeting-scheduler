package com.example.scheduler.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.scheduler.ai.domain.MeetingAiResult;
import com.example.scheduler.ai.domain.MeetingOrchestrationResult;
import com.example.scheduler.ai.domain.MeetingRequestDetails;
import com.example.scheduler.ai.domain.MeetingSchedulePreferences;
import com.example.scheduler.ai.domain.MeetingSuggestion;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingOrchestrationServiceTest {
    @Mock private MeetingWorkflowClient meetingWorkflowClient;
    @InjectMocks private MeetingOrchestrationService service;

    @Test
    void returnsClarificationWithoutCallingMeetingService() {
        var result = service.orchestrate(MeetingAiResult.needsClarification("Provide durationMinutes."));

        assertThat(result.status()).isEqualTo(MeetingOrchestrationResult.Status.NEEDS_CLARIFICATION);
        assertThat(result.clarification()).isEqualTo("Provide durationMinutes.");
        assertThat(result.meetingId()).isNull();
        verifyNoInteractions(meetingWorkflowClient);
    }

    @Test
    void createsMeetingThenRequestsSuggestionsAndReturnsBoth() {
        var details = validDetails();
        var meetingId = UUID.randomUUID();
        var suggestions = List.of(new MeetingSuggestion(Instant.parse("2026-10-12T10:00:00Z"),
                Instant.parse("2026-10-12T10:30:00Z"), true, 1, 1, List.of()));
        given(meetingWorkflowClient.createMeeting(details)).willReturn(meetingId);
        given(meetingWorkflowClient.requestSchedulingSuggestions(eq(meetingId), org.mockito.ArgumentMatchers.any()))
                .willReturn(suggestions);

        var result = service.orchestrate(MeetingAiResult.parsed(details));

        assertThat(result.status()).isEqualTo(MeetingOrchestrationResult.Status.SUGGESTIONS_RETURNED);
        assertThat(result.meetingId()).isEqualTo(meetingId);
        assertThat(result.suggestions()).isEqualTo(suggestions);
        var capturedDetails = ArgumentCaptor.forClass(MeetingRequestDetails.class);
        verify(meetingWorkflowClient).createMeeting(capturedDetails.capture());
        assertThat(capturedDetails.getValue()).isSameAs(details);
        verify(meetingWorkflowClient).requestSchedulingSuggestions(meetingId,
                new MeetingSchedulePreferences(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalTime.of(10, 0)));
    }

    @Test
    void propagatesCreateFailureAndDoesNotRequestSuggestions() {
        var details = validDetails();
        given(meetingWorkflowClient.createMeeting(details))
                .willThrow(new ServiceUnavailableException("Meeting Service create request failed", null));

        assertThatThrownBy(() -> service.orchestrate(MeetingAiResult.parsed(details)))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(meetingWorkflowClient, never()).requestSchedulingSuggestions(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void propagatesSchedulingFailureInsteadOfReportingSuccess() {
        var details = validDetails();
        var meetingId = UUID.randomUUID();
        given(meetingWorkflowClient.createMeeting(details)).willReturn(meetingId);
        given(meetingWorkflowClient.requestSchedulingSuggestions(eq(meetingId), org.mockito.ArgumentMatchers.any()))
                .willThrow(new ServiceUnavailableException("Meeting Service scheduling request failed", null));

        assertThatThrownBy(() -> service.orchestrate(MeetingAiResult.parsed(details)))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void returnsMeetingWhenSchedulingHasNoSuggestions() {
        var details = validDetails();
        var meetingId = UUID.randomUUID();
        given(meetingWorkflowClient.createMeeting(details)).willReturn(meetingId);
        given(meetingWorkflowClient.requestSchedulingSuggestions(eq(meetingId), org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of());

        var result = service.orchestrate(MeetingAiResult.parsed(details));

        assertThat(result.status()).isEqualTo(MeetingOrchestrationResult.Status.SUGGESTIONS_RETURNED);
        assertThat(result.meetingId()).isEqualTo(meetingId);
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void doesNotCreateMeetingWhenParsedDetailsFailParticipantValidation() {
        var invalidDetails = new MeetingRequestDetails("API Design", null, List.of("API design"), 30,
                "owner@example.com", List.of(), List.of(), null, null, null);

        var result = service.orchestrate(MeetingAiResult.parsed(invalidDetails));

        assertThat(result.status()).isEqualTo(MeetingOrchestrationResult.Status.NEEDS_CLARIFICATION);
        assertThat(result.meetingId()).isNull();
        verifyNoInteractions(meetingWorkflowClient);
    }

    private static MeetingRequestDetails validDetails() {
        return new MeetingRequestDetails("API Design", null, List.of("API design", "Database design"), 30,
                "owner@example.com", List.of("required@example.com"), List.of("optional@example.com"),
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalTime.of(10, 0));
    }
}
