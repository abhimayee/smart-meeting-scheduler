package com.example.scheduler.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.scheduler.UserServiceApplication;
import com.example.scheduler.test.support.IntegrationTestDatabaseUrl;
import com.example.scheduler.user.api.UserDtos;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies the local seed user is available through the launcher's readiness endpoint after restarts. */
class DevelopmentUserDataStartupIntegrationTest {
    private static final String DEMO_EMAIL = "scheduler-demo-organizer@example.com";

    @Test
    void seedsDemoUserIdempotentlyAcrossApplicationStarts() {
        // Validate the configured target before opening either application or JDBC connection.
        var database = IntegrationTestDatabaseUrl.fromApplicationYaml("user_service");
        UserDtos.UserResponse firstStart;
        try (ConfigurableApplicationContext first = start()) {
            var response = getDemoUser(first);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            firstStart = response.getBody();
            assertThat(firstStart).isNotNull();
            assertThat(firstStart.email()).isEqualTo(DEMO_EMAIL);
            assertThat(firstStart.displayName()).isEqualTo("Demo Organizer");
            assertThat(firstStart.timeZone()).isEqualTo(ZoneId.of("Asia/Kolkata").getId());
            assertThat(firstStart.workingHours()).hasSize(10);
            assertThat(first.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT count(*) FROM user_service.users WHERE email = ?", Integer.class, DEMO_EMAIL)).isEqualTo(1);
        }

        try (ConfigurableApplicationContext restarted = start()) {
            var response = getDemoUser(restarted);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().id()).isEqualTo(firstStart.id());
            assertThat(response.getBody().createdAt()).isEqualTo(firstStart.createdAt());
            assertThat(response.getBody().workingHours()).containsExactlyElementsOf(firstStart.workingHours());
            assertThat(restarted.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT count(*) FROM user_service.users WHERE email = ?", Integer.class, DEMO_EMAIL)).isEqualTo(1);
            assertThat(restarted.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT count(*) FROM user_service.user_working_hours WHERE user_id = ?",
                    Integer.class, firstStart.id())).isEqualTo(10);
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(UserServiceApplication.class)
                .run("--server.port=0");
    }

    private org.springframework.http.ResponseEntity<UserDtos.UserResponse> getDemoUser(
            ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return new TestRestTemplate().getForEntity(
                "http://127.0.0.1:" + port + "/api/users/{email}", UserDtos.UserResponse.class, DEMO_EMAIL);
    }
}
