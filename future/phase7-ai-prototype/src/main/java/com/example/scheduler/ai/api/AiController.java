package com.example.scheduler.ai.api;

import com.example.scheduler.ai.application.MeetingAiService;
import com.example.scheduler.ai.application.MeetingOrchestrationService;
import com.example.scheduler.ai.domain.MeetingAiResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/meetings")
public class AiController {
    private final MeetingAiService meetingAiService;
    private final MeetingOrchestrationService meetingOrchestrationService;

    public AiController(MeetingAiService meetingAiService, MeetingOrchestrationService meetingOrchestrationService) {
        this.meetingAiService = meetingAiService;
        this.meetingOrchestrationService = meetingOrchestrationService;
    }

    @PostMapping("/parse")
    @ResponseStatus(HttpStatus.OK)
    public AiDtos.ParseMeetingResponse parse(@Valid @RequestBody AiDtos.ParseMeetingRequest request) {
        var result = meetingAiService.parseMeetingRequest(request.prompt());
        return new AiDtos.ParseMeetingResponse(result.status(), result.meetingRequest(), result.clarification());
    }

    @PostMapping("/orchestrate")
    @ResponseStatus(HttpStatus.OK)
    public AiDtos.OrchestrateMeetingResponse orchestrate(
            @Valid @RequestBody AiDtos.OrchestrateMeetingRequest request) {
        var parsed = new MeetingAiResult(request.status(), request.meetingRequest(), request.clarification());
        var result = meetingOrchestrationService.orchestrate(parsed);
        return new AiDtos.OrchestrateMeetingResponse(result.status(), result.clarification(), result.meetingId(),
                result.suggestions());
    }
}
