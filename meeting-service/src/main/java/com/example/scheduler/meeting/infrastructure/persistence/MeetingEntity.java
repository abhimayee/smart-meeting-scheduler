package com.example.scheduler.meeting.infrastructure.persistence;

import com.example.scheduler.meeting.domain.MeetingStatus;
import com.example.scheduler.meeting.domain.MeetingType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "meetings")
public class MeetingEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 300) private String title;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "agenda", nullable = false, columnDefinition = "jsonb")
    private List<String> agendaItems = new ArrayList<>();
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Column(name = "organizer_email", nullable = false, length = 320) private String organizerEmail;
    @Enumerated(EnumType.STRING) @Column(name = "meeting_type", nullable = false, length = 30)
    private MeetingType meetingType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MeetingStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "scheduled_start") private Instant confirmedStart;
    @Column(name = "scheduled_end") private Instant confirmedEnd;
    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC") private List<MeetingParticipantEntity> participants = new ArrayList<>();

    protected MeetingEntity() { }

    public MeetingEntity(UUID id, String title, String agenda, int durationMinutes,
                         String organizerEmail, MeetingStatus status) {
        this(id, title, agenda, durationMinutes, organizerEmail, MeetingType.STANDARD, status);
    }

    public MeetingEntity(UUID id, String title, String agenda, int durationMinutes,
                         String organizerEmail, MeetingType meetingType, MeetingStatus status) {
        this.id = id;
        this.title = title;
        this.agendaItems = List.of(agenda);
        this.durationMinutes = durationMinutes;
        this.organizerEmail = organizerEmail;
        this.meetingType = meetingType == null ? MeetingType.STANDARD : meetingType;
        this.status = status;
    }

    public void addParticipant(MeetingParticipantEntity participant) {
        participants.add(participant);
        participant.setMeeting(this);
    }

    public void updateDetails(String title, String agenda, int durationMinutes,
                              String organizerEmail, MeetingType meetingType) {
        this.title = title;
        this.agendaItems = List.of(agenda);
        this.durationMinutes = durationMinutes;
        this.organizerEmail = organizerEmail;
        this.meetingType = meetingType == null ? MeetingType.STANDARD : meetingType;
    }

    public void replaceParticipants(List<MeetingParticipantEntity> requestedParticipants) {
        Map<String, MeetingParticipantEntity> existingByEmail = new HashMap<>();
        for (var participant : participants) existingByEmail.put(participant.getEmail(), participant);

        var reconciled = new ArrayList<MeetingParticipantEntity>(requestedParticipants.size());
        for (var requested : requestedParticipants) {
            var existing = existingByEmail.remove(requested.getEmail());
            if (existing == null) {
                requested.setMeeting(this);
                reconciled.add(requested);
            } else {
                existing.updateType(requested.getType());
                reconciled.add(existing);
            }
        }

        participants.clear();
        participants.addAll(reconciled);
    }

    public void applyState(MeetingStatus status, Instant confirmedStart, Instant confirmedEnd) {
        this.status = status;
        this.confirmedStart = confirmedStart;
        this.confirmedEnd = confirmedEnd;
    }

    @PrePersist void onCreate() {
        var now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getAgenda() { return String.join("\n", agendaItems); }
    public int getDurationMinutes() { return durationMinutes; }
    public String getOrganizerEmail() { return organizerEmail; }
    public MeetingStatus getStatus() { return status; }
    public MeetingType getMeetingType() { return meetingType; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getConfirmedStart() { return confirmedStart; }
    public Instant getConfirmedEnd() { return confirmedEnd; }
    public List<MeetingParticipantEntity> getParticipants() { return participants; }
}
