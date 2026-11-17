package com.example.scheduler.ai.api;

import com.example.scheduler.ai.domain.MeetingAiResult;
import com.example.scheduler.ai.domain.MeetingOrchestrationResult;
import com.example.scheduler.ai.domain.MeetingRequestDetails;
import com.example.scheduler.ai.domain.MeetingSuggestion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public final class AiDtos {
    private AiDtos() { }
    public record ParseMeetingRequest(@NotBlank String prompt) { }
    public record ParseMeetingResponse(MeetingAiResult.Status status, MeetingRequestDetails meetingRequest,
                                      String clarification) { }
    public record OrchestrateMeetingRequest(@NotNull MeetingAiResult.Status status,
                                            @Valid MeetingRequestDetails meetingRequest,
                                            String clarification) { }
    public record OrchestrateMeetingResponse(MeetingOrchestrationResult.Status status, String clarification,
                                            UUID meetingId, List<MeetingSuggestion> suggestions) { }
}
