package com.example.scheduler.calendar.infrastructure.persistence;

import com.example.scheduler.calendar.domain.CalendarEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEventEntity, UUID> {
    List<CalendarEventEntity> findByUserEmailOrderByStartAtAsc(String email);

    List<CalendarEventEntity> findByUserEmailAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            String email, Instant rangeEnd, Instant rangeStart);

    List<CalendarEventEntity> findByUserEmailAndStatusAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            String email, CalendarEventStatus status, Instant rangeEnd, Instant rangeStart);
}
