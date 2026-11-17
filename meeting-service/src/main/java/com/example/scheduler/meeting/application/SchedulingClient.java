package com.example.scheduler.meeting.application;

import com.example.scheduler.meeting.api.MeetingDtos;
import java.util.List;

/** Boundary for requesting ranked availability suggestions from Scheduling Service. */
public interface SchedulingClient {
    List<MeetingDtos.SlotSuggestionResponse> findSlots(MeetingDtos.FindSlotsRequest request);
}
