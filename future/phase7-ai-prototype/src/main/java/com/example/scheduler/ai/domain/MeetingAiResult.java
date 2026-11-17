package com.example.scheduler.ai.domain;

public record MeetingAiResult(Status status, MeetingRequestDetails meetingRequest, String clarification) {
    public enum Status { COMPLETE, PARSED, NEEDS_CLARIFICATION }

    public MeetingAiResult {
        if (status == null) throw new IllegalArgumentException("AI result status is required");
        if ((status == Status.COMPLETE || status == Status.PARSED)
                && (meetingRequest == null || clarification != null))
            throw new IllegalArgumentException("Parsed results require meeting details and no clarification");
        if (status == Status.NEEDS_CLARIFICATION
                && (meetingRequest != null || clarification == null || clarification.isBlank()))
            throw new IllegalArgumentException("Clarification results require a message and no meeting details");
    }

    public static MeetingAiResult parsed(MeetingRequestDetails request) {
        return new MeetingAiResult(Status.COMPLETE, request, null);
    }

    public static MeetingAiResult needsClarification(String message) {
        return new MeetingAiResult(Status.NEEDS_CLARIFICATION, null, message);
    }
}
