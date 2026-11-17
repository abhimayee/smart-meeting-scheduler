package com.example.scheduler.meeting.application;

import com.example.scheduler.meeting.api.MeetingDtos;
import com.example.scheduler.meeting.domain.Meeting;
import com.example.scheduler.meeting.domain.MeetingParticipant;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingEntity;
import com.example.scheduler.meeting.infrastructure.persistence.MeetingParticipantEntity;
import com.example.scheduler.shared.domain.EmailAddress;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MeetingMapper {
    public Meeting toDomain(MeetingEntity entity) {
        List<MeetingParticipant> participants = entity.getParticipants().stream().map(this::toDomain).toList();
        return new Meeting(entity.getId(), entity.getTitle(), entity.getAgenda(), entity.getDurationMinutes(),
                entity.getOrganizerEmail(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getConfirmedStart(), entity.getConfirmedEnd(), participants);
    }

    public MeetingDtos.MeetingResponse toResponse(MeetingEntity entity) {
        return new MeetingDtos.MeetingResponse(entity.getId(), entity.getTitle(), entity.getAgenda(),
                entity.getDurationMinutes(), entity.getOrganizerEmail(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getConfirmedStart(), entity.getConfirmedEnd(),
                entity.getParticipants().stream().map(this::toResponse).toList());
    }

    public MeetingDtos.ParticipantResponse toResponse(MeetingParticipantEntity entity) {
        return new MeetingDtos.ParticipantResponse(entity.getId(), entity.getMeeting().getId(),
                entity.getEmail(), entity.getType(), entity.getResponseStatus());
    }

    private MeetingParticipant toDomain(MeetingParticipantEntity entity) {
        return new MeetingParticipant(entity.getId(), entity.getMeeting().getId(),
                new EmailAddress(entity.getEmail()), entity.getType(), entity.getResponseStatus());
    }
}
