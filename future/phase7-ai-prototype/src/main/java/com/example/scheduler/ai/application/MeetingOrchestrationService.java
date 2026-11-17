package com.example.scheduler.ai.application;

import com.example.scheduler.ai.domain.MeetingOrchestrationResult;
import com.example.scheduler.ai.domain.MeetingAiResult;
import com.example.scheduler.ai.domain.MeetingSchedulePreferences;
import org.springframework.stereotype.Service;

@Service
public class MeetingOrchestrationService {
    private final MeetingWorkflowClient meetingWorkflowClient;

    public MeetingOrchestrationService(MeetingWorkflowClient meetingWorkflowClient) {
        this.meetingWorkflowClient = meetingWorkflowClient;
    }

    public MeetingOrchestrationResult orchestrate(MeetingAiResult parsed) {
        if (parsed.status() == MeetingAiResult.Status.NEEDS_CLARIFICATION)
            return MeetingOrchestrationResult.clarification(parsed.clarification());

        var details = parsed.meetingRequest();
        if (details.requiredParticipants().isEmpty())
            return MeetingOrchestrationResult.clarification("At least one required participant is needed to schedule a meeting.");

        var meetingId = meetingWorkflowClient.createMeeting(details);
        var preferences = new MeetingSchedulePreferences(details.preferredStartDate(), details.preferredEndDate(),
                details.preferredStartTime(), details.preferredTimeZone());
        var suggestions = meetingWorkflowClient.requestSchedulingSuggestions(meetingId, preferences);
        return MeetingOrchestrationResult.suggestions(meetingId, suggestions);
    }
}
