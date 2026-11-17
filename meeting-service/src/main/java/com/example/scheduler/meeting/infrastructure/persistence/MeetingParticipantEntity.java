package com.example.scheduler.meeting.infrastructure.persistence;

import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.meeting.domain.ResponseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "meeting_participants")
public class MeetingParticipantEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false) private MeetingEntity meeting;
    @Column(nullable = false, length = 320) private String email;
    @Enumerated(EnumType.STRING) @Column(name = "participant_type", nullable = false, length = 20) private ParticipantType type;
    @Enumerated(EnumType.STRING) @Column(name = "response_status", nullable = false, length = 20) private ResponseStatus responseStatus = ResponseStatus.PENDING;

    protected MeetingParticipantEntity() { }

    public MeetingParticipantEntity(UUID id, String email, ParticipantType type) {
        this.id = id;
        this.email = email;
        this.type = type;
    }

    void setMeeting(MeetingEntity meeting) { this.meeting = meeting; }
    void updateType(ParticipantType type) { this.type = type; }
    public UUID getId() { return id; }
    public MeetingEntity getMeeting() { return meeting; }
    public String getEmail() { return email; }
    public ParticipantType getType() { return type; }
    public ResponseStatus getResponseStatus() { return responseStatus; }
}
