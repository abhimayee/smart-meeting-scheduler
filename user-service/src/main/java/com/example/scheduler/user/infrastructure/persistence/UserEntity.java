package com.example.scheduler.user.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
public class UserEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "time_zone", nullable = false, length = 100)
    private String timeZone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dayOfWeek ASC, startTime ASC")
    private List<UserWorkingHoursEntity> workingHours = new ArrayList<>();

    protected UserEntity() { }

    public UserEntity(UUID id, String email, String displayName, String timeZone) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.timeZone = timeZone;
    }

    public void replaceWorkingHours(List<UserWorkingHoursEntity> entries) {
        workingHours.clear();
        entries.forEach(this::addWorkingHours);
        touch();
    }

    public void touch() { updatedAt = Instant.now(); }

    public void addWorkingHours(UserWorkingHoursEntity entry) {
        workingHours.add(entry);
        entry.setUser(this);
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        var now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getTimeZone() { return timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<UserWorkingHoursEntity> getWorkingHours() { return workingHours; }
}
