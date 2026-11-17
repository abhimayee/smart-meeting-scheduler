package com.example.scheduler.meeting.api;

import com.example.scheduler.meeting.application.MeetingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/meetings")
public class MeetingController {
    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) { this.meetingService = meetingService; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MeetingDtos.MeetingResponse create(@Valid @RequestBody MeetingDtos.CreateMeetingRequest request) { return meetingService.create(request); }
    @GetMapping("/{meetingId}") public MeetingDtos.MeetingResponse get(@PathVariable UUID meetingId) { return meetingService.get(meetingId); }
    @PutMapping("/{meetingId}") public MeetingDtos.MeetingResponse update(@PathVariable UUID meetingId,
            @Valid @RequestBody MeetingDtos.CreateMeetingRequest request) {
        return meetingService.update(meetingId, request);
    }
    @PostMapping("/{meetingId}/schedule") public MeetingDtos.ScheduleMeetingResponse schedule(@PathVariable UUID meetingId,
            @Valid @RequestBody(required = false) MeetingDtos.ScheduleMeetingRequest request) {
        return request == null ? meetingService.schedule(meetingId) : meetingService.schedule(meetingId, request);
    }
    @GetMapping("/{meetingId}/participants") public List<MeetingDtos.ParticipantResponse> participants(@PathVariable UUID meetingId) {
        return meetingService.getParticipants(meetingId);
    }
    @PostMapping("/{meetingId}/confirm") public MeetingDtos.MeetingResponse confirm(@PathVariable UUID meetingId,
            @Valid @RequestBody MeetingDtos.ConfirmMeetingRequest request) { return meetingService.confirm(meetingId, request); }
    @PostMapping("/{meetingId}/cancel") public MeetingDtos.MeetingResponse cancel(@PathVariable UUID meetingId) {
        return meetingService.cancel(meetingId);
    }
}
