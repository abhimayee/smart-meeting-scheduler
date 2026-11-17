package com.example.scheduler.user.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import java.time.ZoneId;

public record UserProfile(EmailAddress email, String name, ZoneId timeZone, WorkingHours workingHours) {
    public UserProfile {
        if (email == null || timeZone == null) throw new IllegalArgumentException("Email and time zone are required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name must not be blank");
        name = name.trim();
        workingHours = workingHours == null ? new WorkingHours(null) : workingHours;
    }
}
