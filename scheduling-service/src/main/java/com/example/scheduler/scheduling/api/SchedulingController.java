package com.example.scheduler.scheduling.api;

import com.example.scheduler.scheduling.application.SchedulingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheduling")
public class SchedulingController {
    private final SchedulingService schedulingService;

    public SchedulingController(SchedulingService schedulingService) { this.schedulingService = schedulingService; }

    @PostMapping("/find-slots")
    public SchedulingDtos.FindSlotsResponse findSlots(@Valid @RequestBody SchedulingDtos.FindSlotsRequest request) {
        var suggestions = schedulingService.findSlots(request).stream().map(slot -> new SchedulingDtos.SlotSuggestionResponse(
                slot.interval().start(), slot.interval().end(), slot.requiredParticipantsAvailable(),
                slot.optionalParticipantsAvailable(), slot.optionalParticipantsTotal(),
                slot.participants().stream().map(participant -> new SchedulingDtos.ParticipantSlotResponse(
                        participant.email().value(), participant.type(), participant.available(),
                        participant.localStart(), participant.localEnd())).toList())).toList();
        return new SchedulingDtos.FindSlotsResponse(suggestions);
    }
}
