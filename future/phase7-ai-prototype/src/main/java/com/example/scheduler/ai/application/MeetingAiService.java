package com.example.scheduler.ai.application;

import com.example.scheduler.ai.domain.MeetingAiResult;

public interface MeetingAiService {
    MeetingAiResult parseMeetingRequest(String prompt);
}
