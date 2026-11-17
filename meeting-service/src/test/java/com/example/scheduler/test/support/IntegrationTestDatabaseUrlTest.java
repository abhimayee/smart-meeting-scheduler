package com.example.scheduler.test.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IntegrationTestDatabaseUrlTest {
    @Test
    void rejectsMissingUrl() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(null, "meeting_service"));
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(" ", "meeting_service"));
    }

    @Test
    void rejectsDevelopmentDatabase() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "jdbc:postgresql://localhost:5432/smart_meeting_scheduler", "meeting_service"));
    }

    @Test
    void rejectsOtherDatabaseNames() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "jdbc:postgresql://localhost:5432/another_database", "meeting_service"));
    }

    @Test
    void rejectsCurrentSchemaParameter() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "jdbc:postgresql://localhost:5432/smart_meeting_scheduler_it?currentSchema=public", "meeting_service"));
    }

    @Test
    void acceptsOnlyDedicatedTestDatabaseAndAppendsOwnedSchema() {
        assertEquals("jdbc:postgresql://localhost:5432/smart_meeting_scheduler_it?currentSchema=meeting_service",
                IntegrationTestDatabaseUrl.forSchema(
                        "jdbc:postgresql://localhost:5432/smart_meeting_scheduler_it", "meeting_service"));
    }

    @Test
    void rejectsMalformedJdbcUrl() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "not-a-jdbc-url", "meeting_service"));
    }
}
