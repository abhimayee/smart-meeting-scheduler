package com.example.scheduler.user.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.scheduler.UserServiceApplication;
import com.example.scheduler.test.support.IntegrationTestDatabaseUrl;
import com.example.scheduler.user.api.UserDtos;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.HttpStatus;

@ActiveProfiles("test")
@SpringBootTest(classes = UserServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserPostgresIntegrationTest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    private String createdEmail;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        var database = IntegrationTestDatabaseUrl.fromApplicationYaml("user_service");
        properties.add("spring.datasource.url", database::url);
        properties.add("spring.datasource.username", database::username);
        properties.add("spring.datasource.password", database::password);
    }

    @Test
    void flywayCreatesSchemaAndUserAndWorkingHoursPersistAcrossHttpRequests() {
        Integer applied = jdbc.queryForObject("SELECT count(*) FROM user_service.flyway_schema_history "
                + "WHERE version = '1' AND success = true", Integer.class);
        assertEquals(1, applied);

        String email = "it-" + UUID.randomUUID() + "@example.test";
        createdEmail = email;
        var hours = List.of(new UserDtos.WorkingHoursRequest(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)),
                new UserDtos.WorkingHoursRequest(DayOfWeek.MONDAY,
                        LocalTime.of(13, 0), LocalTime.of(17, 0)));
        var create = new UserDtos.CreateUserRequest(email, "Postgres Integration", "Asia/Kolkata", hours);
        var created = http.postForEntity(url("/api/users"), create, UserDtos.UserResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertNotNull(created.getBody());

        var retrieved = http.getForEntity(url("/api/users/{email}"), UserDtos.UserResponse.class, email);
        assertEquals(HttpStatus.OK, retrieved.getStatusCode());
        assertNotNull(retrieved.getBody());
        assertEquals(created.getBody().id(), retrieved.getBody().id());
        assertEquals(email, retrieved.getBody().email());
        assertEquals("Asia/Kolkata", retrieved.getBody().timeZone());
        assertEquals(hours, retrieved.getBody().workingHours());
        // PostgreSQL rounds timestamps to microsecond precision on persistence.
        assertTrue(java.time.Duration.between(created.getBody().createdAt(), retrieved.getBody().createdAt())
                .abs().compareTo(java.time.Duration.ofNanos(1_000)) <= 0);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_service.users WHERE email = ?", Integer.class, email));
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    @org.junit.jupiter.api.AfterEach
    void removeFixture() {
        if (createdEmail != null) {
            jdbc.update("DELETE FROM user_service.users WHERE email = ?", createdEmail);
            createdEmail = null;
        }
    }
}
