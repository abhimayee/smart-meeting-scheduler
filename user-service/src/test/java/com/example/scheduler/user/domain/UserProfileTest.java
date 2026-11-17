package com.example.scheduler.user.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {
    @Test void requiresValidIanaZoneAndName() {
        assertDoesNotThrow(() -> new UserProfile(new EmailAddress("a@example.com"), "A", ZoneId.of("Asia/Kolkata"), null));
        assertThrows(Exception.class, () -> ZoneId.of("Not/AZone"));
        assertThrows(IllegalArgumentException.class, () -> new UserProfile(new EmailAddress("a@example.com"), " ", ZoneId.of("UTC"), null));
    }
}
