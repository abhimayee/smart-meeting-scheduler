package com.example.scheduler.test.support;

import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

public final class IntegrationTestDatabaseUrl {
    private static final Pattern POSTGRES_URL = Pattern.compile(
            "(?i)^jdbc:postgresql://[^/?#]+/([^/?#]+)(?:\\?[^#]*)?$");
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^:}]+)(?::(.*))?}$");

    private IntegrationTestDatabaseUrl() { }

    public static String forSchema(String url, String schema) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("IT_DB_URL is required and must target smart_meeting_scheduler_it");
        }
        if (url.toLowerCase(Locale.ROOT).contains("currentschema")) {
            throw new IllegalStateException("IT_DB_URL must not contain currentSchema; the test selects its own schema");
        }

        Matcher matcher = POSTGRES_URL.matcher(url);
        if (!matcher.matches()) {
            throw new IllegalStateException("IT_DB_URL must be a valid PostgreSQL JDBC URL for smart_meeting_scheduler_it");
        }

        String databaseName = matcher.group(1);
        if (databaseName.equals("smart_meeting_scheduler")) {
            throw new IllegalStateException("IT_DB_URL must not target the development database smart_meeting_scheduler");
        }
        if (!databaseName.equals("smart_meeting_scheduler_it")) {
            throw new IllegalStateException("IT_DB_URL must target the dedicated database smart_meeting_scheduler_it");
        }

        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    public static DatabaseSettings fromApplicationYaml(String schema) {
        try {
            var source = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).getFirst();
            String configuredUrl = required(source.getProperty("spring.datasource.url"), "spring.datasource.url");
            Matcher currentSchema = Pattern.compile("(?i)(?:^|[?&])currentSchema=([^&]+)").matcher(configuredUrl);
            if (!currentSchema.find() || !currentSchema.group(1).equals(schema)) {
                throw new IllegalStateException("The service datasource must select its own " + schema + " schema");
            }
            String baseUrl = configuredUrl.replaceFirst("(?i)[?&]currentSchema=[^&]*", "");
            return new DatabaseSettings(forSchema(baseUrl, schema),
                    resolveConfiguredValue(source.getProperty("spring.datasource.username"), "spring.datasource.username"),
                    resolveConfiguredValue(source.getProperty("spring.datasource.password"), "spring.datasource.password"));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not load the service datasource configuration", exception);
        }
    }

    private static String resolveConfiguredValue(Object configured, String property) {
        String value = required(configured, property);
        Matcher matcher = PLACEHOLDER.matcher(value);
        if (!matcher.matches()) return value;
        String key = matcher.group(1);
        String resolved = System.getenv(key);
        if (resolved == null) resolved = System.getProperty(key);
        if (resolved == null) resolved = dotenvValue(key);
        if (resolved == null) resolved = matcher.group(2);
        return required(resolved, property);
    }

    private static String dotenvValue(String key) {
        Path directory = Path.of("").toAbsolutePath().normalize();
        while (directory != null) {
            Path envFile = directory.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                try {
                    for (String line : Files.readAllLines(envFile)) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith(key + "=")) {
                            String value = trimmed.substring(key.length() + 1).trim();
                            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                                    || (value.startsWith("'") && value.endsWith("'")))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            return value;
                        }
                    }
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException("Could not read local datasource environment configuration", exception);
                }
            }
            directory = directory.getParent();
        }
        return null;
    }

    private static String required(Object value, String property) {
        if (value == null || value.toString().isBlank())
            throw new IllegalStateException(property + " must be configured for PostgreSQL integration tests");
        return value.toString();
    }

    public record DatabaseSettings(String url, String username, String password) { }
}
