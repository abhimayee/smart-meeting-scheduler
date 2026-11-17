package com.example.scheduler.shared.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailAddressTest {
    @Test void normalizesEmailForComparison() {
        assertEquals(new EmailAddress(" Person@Example.COM "), new EmailAddress("person@example.com"));
        assertEquals("person@example.com", new EmailAddress(" Person@Example.COM ").value());
    }
    @Test void rejectsMalformedEmail() { assertThrows(IllegalArgumentException.class, () -> new EmailAddress("not-an-email")); }
}
