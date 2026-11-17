package com.example.scheduler.ai.domain;

import com.example.scheduler.shared.domain.ParticipantType;
import java.time.Instant;
import java.util.List;

/** AI-service-owned representation of the existing Meeting Service scheduling response. */
public record MeetingSuggestion(Instant start, Instant end, boolean requiredParticipantsAvailable,
                                int optionalParticipantsAvailable, int optionalParticipantsTotal,
                                List<ParticipantSuggestion> participants) {
    public MeetingSuggestion {
        participants = participants == null ? List.of() : List.copyOf(participants);
    }

    public record ParticipantSuggestion(String email, ParticipantType type, boolean available,
                                        String localStart, String localEnd) { }
}
