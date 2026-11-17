package com.example.scheduler.calendar.infrastructure.persistence;

import com.example.scheduler.calendar.domain.CalendarEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_events")
public class CalendarEventEntity {
    @Id private UUID id;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(length = 300)
    private String title;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(nullable = false)
    private boolean busy = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CalendarEventStatus status = CalendarEventStatus.BUSY;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CalendarEventEntity() { }

    public CalendarEventEntity(UUID id, String userEmail, String title, Instant startAt, Instant endAt) {
        this.id = id;
        this.userEmail = userEmail;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void cancel() {
        status = CalendarEventStatus.CANCELLED;
        busy = false;
    }

    @PrePersist void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
        syncBusyFlag();
    }

    @PreUpdate void onUpdate() {
        updatedAt = Instant.now();
        syncBusyFlag();
    }

    private void syncBusyFlag() { busy = status == CalendarEventStatus.BUSY; }

    public UUID getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public String getTitle() { return title; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public boolean isBusy() { return busy; }
    public CalendarEventStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
