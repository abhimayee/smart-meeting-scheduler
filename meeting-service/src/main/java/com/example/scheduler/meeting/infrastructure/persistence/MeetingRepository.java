package com.example.scheduler.meeting.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetingRepository extends JpaRepository<MeetingEntity, UUID> {
    @EntityGraph(attributePaths = {"participants"})
    Optional<MeetingEntity> findWithDetailsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select meeting from MeetingEntity meeting where meeting.id = :id")
    Optional<MeetingEntity> findForUpdateById(@Param("id") UUID id);
}
