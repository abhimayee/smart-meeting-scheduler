package com.example.scheduler.ai.integration;

import com.example.scheduler.test.support.IntegrationTestDatabaseUrl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Starts the real service applications on ephemeral local ports for the reactor E2E tests. */
final class InProcessServiceStack implements AutoCloseable {
    private static final String DATABASE_AUTOCONFIG_EXCLUSIONS =
            "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration";
    private final Path projectRoot = findProjectRoot();
    private final List<ConfigurableApplicationContext> contexts = new ArrayList<>();
    private final List<URLClassLoader> serviceClassLoaders = new ArrayList<>();
    private ConfigurableApplicationContext user;
    private ConfigurableApplicationContext calendar;
    private ConfigurableApplicationContext scheduling;
    private ConfigurableApplicationContext meeting;
    private ConfigurableApplicationContext ai;

    void startBackends() {
        // Validate every datasource before starting any context that can run Flyway.
        var userDatabase = database("user-service", "user_service");
        var calendarDatabase = database("calendar-service", "calendar_service");
        var meetingDatabase = database("meeting-service", "meeting_service");

        user = start("com.example.scheduler.UserServiceApplication", "user-service", userDatabase);
        calendar = start("com.example.scheduler.CalendarServiceApplication", "calendar-service", calendarDatabase,
                "--user-service.url=" + url(user));
        scheduling = start("com.example.scheduler.SchedulingServiceApplication", "scheduling-service", null,
                "--calendar-service.url=" + url(calendar), DATABASE_AUTOCONFIG_EXCLUSIONS);
        meeting = start("com.example.scheduler.MeetingServiceApplication", "meeting-service", meetingDatabase,
                "--user-service.url=" + url(user), "--scheduling-service.url=" + url(scheduling));
    }

    void startAi() {
        ai = start(AiIntegrationTestApplication.class.getName(), "future/phase7-ai-prototype", null,
                "--meeting-service.url=" + url(meeting), "--ai.provider=mock", DATABASE_AUTOCONFIG_EXCLUSIONS);
    }

    int aiPort() { return port(ai); }
    String userUrl() { return url(user); }
    String calendarUrl() { return url(calendar); }
    String schedulingUrl() { return url(scheduling); }
    String meetingUrl() { return url(meeting); }
    JdbcTemplate userJdbc() { return user.getBean(JdbcTemplate.class); }
    JdbcTemplate calendarJdbc() { return calendar.getBean(JdbcTemplate.class); }
    JdbcTemplate meetingJdbc() { return meeting.getBean(JdbcTemplate.class); }

    private IntegrationTestDatabaseUrl.DatabaseSettings database(String module, String schema) {
        return IntegrationTestDatabaseUrl.fromApplicationYaml(
                projectRoot.resolve(module).resolve("src/main/resources/application.yml"), schema);
    }

    private ConfigurableApplicationContext start(String applicationName, String module,
            IntegrationTestDatabaseUrl.DatabaseSettings database, String... extraArguments) {
        Path config = projectRoot.resolve(module).resolve("src/main/resources/application.yml");
        if (!Files.isRegularFile(config)) throw new IllegalStateException("Missing service configuration: " + config);
        var arguments = new ArrayList<String>();
        arguments.add("--spring.config.location=" + config.toUri());
        arguments.add("--server.address=127.0.0.1");
        arguments.add("--server.port=0");
        arguments.add("--spring.profiles.active=test");
        if (database != null) {
            String migrations = projectRoot.resolve(module).resolve("src/main/resources/db/migration")
                    .toAbsolutePath().toString().replace('\\', '/');
            arguments.add("--spring.flyway.locations=filesystem:" + migrations);
            arguments.add("--spring.datasource.url=" + database.url());
            arguments.add("--spring.datasource.username=" + database.username());
            arguments.add("--spring.datasource.password=" + database.password());
            arguments.add("--spring.datasource.driver-class-name=org.postgresql.Driver");
        }
        arguments.addAll(List.of(extraArguments));
        ClassLoader serviceLoader = Thread.currentThread().getContextClassLoader();
        if (!module.equals("future/phase7-ai-prototype")) {
            Path classes = projectRoot.resolve(module).resolve("target/classes");
            try {
                serviceLoader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, serviceLoader);
                serviceClassLoaders.add((URLClassLoader) serviceLoader);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Could not load compiled service classes for " + module, exception);
            }
        }
        Class<?> application;
        try {
            application = Class.forName(applicationName, true, serviceLoader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load the service application " + applicationName, exception);
        }
        var builder = new SpringApplicationBuilder(application).profiles("test");
        if (database == null) {
            builder.properties(Map.of("spring.autoconfigure.exclude", List.of(
                    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                    "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                    "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")));
        }
        if (database != null) {
            builder.initializers(context -> context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("dedicatedIntegrationDatabase", Map.of(
                            "spring.datasource.url", database.url(),
                            "spring.datasource.username", database.username(),
                            "spring.datasource.password", database.password(),
                            "spring.datasource.driver-class-name", "org.postgresql.Driver"))));
        }
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        ConfigurableApplicationContext context;
        try {
            thread.setContextClassLoader(serviceLoader);
            context = builder.run(arguments.toArray(String[]::new));
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
        contexts.add(context);
        return context;
    }

    private String url(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + port(context);
    }

    private int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isRegularFile(current.resolve("user-service/src/main/resources/application.yml"))
                    && Files.isRegularFile(current.resolve("future/phase7-ai-prototype/pom.xml"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the Smart Meeting Scheduler reactor root");
    }

    @Override
    public void close() {
        for (int index = contexts.size() - 1; index >= 0; index--) contexts.get(index).close();
        contexts.clear();
        for (int index = serviceClassLoaders.size() - 1; index >= 0; index--) {
            try { serviceClassLoaders.get(index).close(); }
            catch (java.io.IOException ignored) { }
        }
        serviceClassLoaders.clear();
    }
}
