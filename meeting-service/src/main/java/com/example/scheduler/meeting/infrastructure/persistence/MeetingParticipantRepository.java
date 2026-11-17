package com.example.scheduler.meeting.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipantEntity, UUID> {
    List<MeetingParticipantEntity> findByMeeting_IdOrderByIdAsc(UUID meetingId);
}
