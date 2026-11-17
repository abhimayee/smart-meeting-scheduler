package com.example.scheduler.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.scheduler.UserServiceApplication;
import com.example.scheduler.test.support.IntegrationTestDatabaseUrl;
import com.example.scheduler.user.api.UserDtos;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Proves user data can be read after a fresh application context starts against the same database. */
class UserPersistenceRestartIntegrationTest {
    @Test
    void userAndWorkingHoursSurviveApplicationContextRestart() {
        // Refuse to start either context unless the datasource YAML names the dedicated test database.
        var database = IntegrationTestDatabaseUrl.fromApplicationYaml("user_service");
        String email = "restart-it-" + UUID.randomUUID() + "@example.test";
        UserDtos.UserResponse created;

        try {
            try (ConfigurableApplicationContext first = startUserService()) {
                var request = new UserDtos.CreateUserRequest(email, "Restart Integration", "Asia/Kolkata",
                        java.util.List.of(new UserDtos.WorkingHoursRequest(java.time.DayOfWeek.MONDAY,
                                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(12, 0))));
                var response = new TestRestTemplate().postForEntity(url(first, "/api/users"), request,
                        UserDtos.UserResponse.class);
                assertThat(response.getStatusCode().value()).isEqualTo(201);
                created = response.getBody();
                assertThat(created).isNotNull();
            }

            try (ConfigurableApplicationContext restarted = startUserService()) {
                var retrieved = new TestRestTemplate().getForEntity(url(restarted, "/api/users/{email}"),
                        UserDtos.UserResponse.class, email);
                assertThat(retrieved.getStatusCode().value()).isEqualTo(200);
                assertThat(retrieved.getBody().id()).isEqualTo(created.id());
                assertThat(retrieved.getBody().email()).isEqualTo(email);
                assertThat(retrieved.getBody().timeZone()).isEqualTo(ZoneId.of("Asia/Kolkata").getId());
                assertThat(retrieved.getBody().workingHours()).containsExactly(
                        new UserDtos.WorkingHoursRequest(java.time.DayOfWeek.MONDAY,
                                java.time.LocalTime.of(9, 0), java.time.LocalTime.of(12, 0)));
            }
        } finally {
            var dataSource = new DriverManagerDataSource(database.url(), database.username(), database.password());
            new JdbcTemplate(dataSource).update("DELETE FROM user_service.users WHERE email = ?", email);
        }
    }

    private ConfigurableApplicationContext startUserService() {
        return new SpringApplicationBuilder(UserServiceApplication.class)
                .profiles("test")
                .run("--server.port=0");
    }

    private String url(ConfigurableApplicationContext context, String path) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return "http://127.0.0.1:" + port + path;
    }
}
