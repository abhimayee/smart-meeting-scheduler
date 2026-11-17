package com.example.scheduler.test.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IntegrationTestDatabaseUrlTest {
    @Test
    void rejectsMissingUrl() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(null, "ai_test_guard"));
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(" ", "ai_test_guard"));
    }

    @Test
    void rejectsDevelopmentDatabase() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "jdbc:postgresql://localhost:5432/smart_meeting_scheduler", "ai_test_guard"));
    }

    @Test
    void rejectsOtherDatabaseNames() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "jdbc:postgresql://localhost:5432/another_database", "ai_test_guard"));
    }

    @Test
    void rejectsCurrentSchemaParameter() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "jdbc:postgresql://localhost:5432/smart_meeting_scheduler_it?currentSchema=public", "ai_test_guard"));
    }

    @Test
    void acceptsOnlyDedicatedTestDatabase() {
        assertEquals("jdbc:postgresql://localhost:5432/smart_meeting_scheduler_it?currentSchema=ai_test_guard",
                IntegrationTestDatabaseUrl.forSchema(
                        "jdbc:postgresql://localhost:5432/smart_meeting_scheduler_it", "ai_test_guard"));
    }

    @Test
    void rejectsMalformedJdbcUrl() {
        assertThrows(IllegalStateException.class, () -> IntegrationTestDatabaseUrl.forSchema(
                "not-a-jdbc-url", "ai_test_guard"));
    }
}
