package com.example.scheduler.ai.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.scheduler.ai.application.MeetingAiService;
import com.example.scheduler.ai.application.MeetingOrchestrationService;
import com.example.scheduler.ai.domain.MeetingAiResult;
import com.example.scheduler.ai.domain.MeetingOrchestrationResult;
import com.example.scheduler.ai.domain.MeetingRequestDetails;
import com.example.scheduler.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.time.LocalTime;

@WebMvcTest(AiController.class)
@Import(GlobalExceptionHandler.class)
class AiControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private MeetingAiService meetingAiService;
    @MockitoBean private MeetingOrchestrationService meetingOrchestrationService;

    @Test
    void delegatesPromptAndReturnsStructuredResult() throws Exception {
        given(meetingAiService.parseMeetingRequest("title: Review"))
                .willReturn(MeetingAiResult.needsClarification("Provide agenda and durationMinutes."));

        mockMvc.perform(post("/api/ai/meetings/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"title: Review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"))
                .andExpect(jsonPath("$.meetingRequest").doesNotExist())
                .andExpect(jsonPath("$.clarification").value("Provide agenda and durationMinutes."));

        verify(meetingAiService).parseMeetingRequest("title: Review");
    }

    @Test
    void rejectsBlankPromptAtApiBoundary() throws Exception {
        mockMvc.perform(post("/api/ai/meetings/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesOrchestrationResult() throws Exception {
        var meetingId = UUID.randomUUID();
        var details = validDetails();
        given(meetingOrchestrationService.orchestrate(MeetingAiResult.parsed(details)))
                .willReturn(MeetingOrchestrationResult.suggestions(meetingId, List.of()));

        mockMvc.perform(post("/api/ai/meetings/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETE","meetingRequest":{"title":"Review","description":null,
                                "agenda":["Agenda"],"durationMinutes":30,"organizerEmail":"owner@example.com",
                                "requiredParticipants":["attendee@example.com"],"optionalParticipants":[],
                                "preferredStartDate":null,"preferredEndDate":null,"preferredStartTime":null,
                                "preferredTimeZone":null},"clarification":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUGGESTIONS_RETURNED"))
                .andExpect(jsonPath("$.meetingId").value(meetingId.toString()))
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    void passesTheSingleParsedResultIntoOrchestrationWithoutReparsing() throws Exception {
        var details = validDetails();
        var meetingId = UUID.randomUUID();
        given(meetingAiService.parseMeetingRequest("title: Review"))
                .willReturn(MeetingAiResult.parsed(details));
        given(meetingOrchestrationService.orchestrate(MeetingAiResult.parsed(details)))
                .willReturn(MeetingOrchestrationResult.suggestions(meetingId, List.of()));

        mockMvc.perform(post("/api/ai/meetings/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"title: Review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETE"));

        mockMvc.perform(post("/api/ai/meetings/orchestrate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETE","meetingRequest":{"title":"Review","description":null,
                                "agenda":["Agenda"],"durationMinutes":30,"organizerEmail":"owner@example.com",
                                "requiredParticipants":["attendee@example.com"],"optionalParticipants":[],
                                "preferredStartDate":null,"preferredEndDate":null,"preferredStartTime":null,
                                "preferredTimeZone":null},"clarification":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUGGESTIONS_RETURNED"));

        verify(meetingAiService).parseMeetingRequest("title: Review");
        verify(meetingOrchestrationService).orchestrate(MeetingAiResult.parsed(details));
    }

    private static MeetingRequestDetails validDetails() {
        return new MeetingRequestDetails("Review", null, List.of("Agenda"), 30, "owner@example.com",
                List.of("attendee@example.com"), List.of(), (LocalDate) null, null, (LocalTime) null);
    }
}
