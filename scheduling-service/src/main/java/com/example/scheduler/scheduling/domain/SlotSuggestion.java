package com.example.scheduler.scheduling.domain;

import com.example.scheduler.shared.domain.InstantRange;
import java.util.List;

public record SlotSuggestion(InstantRange interval, boolean requiredParticipantsAvailable,
                             int optionalParticipantsAvailable, int optionalParticipantsTotal,
                             List<ParticipantAvailability> participants) {
    public SlotSuggestion {
        if (interval == null) throw new IllegalArgumentException("Slot interval is required");
        if (optionalParticipantsAvailable < 0 || optionalParticipantsTotal < optionalParticipantsAvailable)
            throw new IllegalArgumentException("Optional availability counts are invalid");
        participants = participants == null ? List.of() : List.copyOf(participants);
    }
}
