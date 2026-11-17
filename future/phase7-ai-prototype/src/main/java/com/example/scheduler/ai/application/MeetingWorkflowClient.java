package com.example.scheduler.ai.application;

import com.example.scheduler.ai.domain.MeetingRequestDetails;
import com.example.scheduler.ai.domain.MeetingSchedulePreferences;
import com.example.scheduler.ai.domain.MeetingSuggestion;
import java.util.List;
import java.util.UUID;

/** HTTP boundary to the Meeting Service; the AI service has no persistence access. */
public interface MeetingWorkflowClient {
    UUID createMeeting(MeetingRequestDetails request);
    default List<MeetingSuggestion> requestSchedulingSuggestions(UUID meetingId) {
        return requestSchedulingSuggestions(meetingId, new MeetingSchedulePreferences(null, null, null));
    }
    List<MeetingSuggestion> requestSchedulingSuggestions(UUID meetingId, MeetingSchedulePreferences preferences);
}
