package com.example.scheduler.ai.domain;

import java.util.List;
import java.util.UUID;

public record MeetingOrchestrationResult(Status status, String clarification, UUID meetingId,
                                         List<MeetingSuggestion> suggestions) {
    public enum Status { NEEDS_CLARIFICATION, SUGGESTIONS_RETURNED }

    public MeetingOrchestrationResult {
        if (status == null) throw new IllegalArgumentException("Orchestration status is required");
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        if (status == Status.NEEDS_CLARIFICATION
                && (clarification == null || clarification.isBlank() || meetingId != null || !suggestions.isEmpty()))
            throw new IllegalArgumentException("Clarification results require only a clarification message");
        if (status == Status.SUGGESTIONS_RETURNED && (meetingId == null || clarification != null))
            throw new IllegalArgumentException("Scheduling results require a meeting identifier");
    }

    public static MeetingOrchestrationResult clarification(String message) {
        return new MeetingOrchestrationResult(Status.NEEDS_CLARIFICATION, message, null, List.of());
    }

    public static MeetingOrchestrationResult suggestions(UUID meetingId, List<MeetingSuggestion> suggestions) {
        return new MeetingOrchestrationResult(Status.SUGGESTIONS_RETURNED, null, meetingId, suggestions);
    }
}
