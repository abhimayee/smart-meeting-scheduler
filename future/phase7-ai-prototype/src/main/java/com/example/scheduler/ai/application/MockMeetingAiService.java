package com.example.scheduler.ai.application;

import com.example.scheduler.ai.domain.MeetingAiResult;
import com.example.scheduler.ai.domain.MeetingRequestDetails;
import com.example.scheduler.shared.domain.EmailAddress;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockMeetingAiService implements MeetingAiService {
    private static final Set<String> REQUIRED_FIELDS = Set.of("title", "agenda", "durationMinutes", "organizerEmail");
    private static final Set<String> ALLOWED_FIELDS = Set.of("title", "description", "agenda", "durationMinutes",
            "organizerEmail", "requiredParticipants", "optionalParticipants", "preferredStartDate",
            "preferredEndDate", "preferredStartTime", "preferredTimeZone");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Parses a deliberately narrow key/value prompt, one field per line. Agenda items use `|`;
     * participant emails use commas. Preference dates/times default to the organizer's time zone;
     * `preferredTimeZone` may provide a separate IANA zone.
     * Supported keys are listed in {@link #ALLOWED_FIELDS}.
     * This mock does not interpret unrestricted natural language.
     */
    @Override
    public MeetingAiResult parseMeetingRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("Prompt must not be blank");

        try {
            Map<String, String> fields = parseFields(prompt);
            List<String> missing = REQUIRED_FIELDS.stream()
                    .filter(field -> fields.get(field) == null || fields.get(field).isBlank())
                    .sorted().toList();
            if (!missing.isEmpty())
                return clarification("Please provide the required field(s): " + String.join(", ", missing) + ".");

            String title = fields.get("title").trim();
            if (title.length() > 300) return clarification("The title must not exceed 300 characters.");
            List<String> agenda = splitAgenda(fields.get("agenda"));
            int durationMinutes = parseDuration(fields.get("durationMinutes"));
            String organizerEmail = normalizeEmail(fields.get("organizerEmail"), "organizerEmail");
            List<String> requiredParticipants = parseEmails(fields.get("requiredParticipants"), "requiredParticipants");
            List<String> optionalParticipants = parseEmails(fields.get("optionalParticipants"), "optionalParticipants");
            LocalDate startDate = parseDate(fields.get("preferredStartDate"), "preferredStartDate");
            LocalDate endDate = parseDate(fields.get("preferredEndDate"), "preferredEndDate");
            if ((startDate == null) != (endDate == null))
                return clarification("Provide both preferredStartDate and preferredEndDate, or omit both.");
            LocalTime preferredStartTime = parseTime(fields.get("preferredStartTime"));
            ZoneId preferredTimeZone = parseZone(fields.get("preferredTimeZone"));

            try {
                return MeetingAiResult.parsed(new MeetingRequestDetails(title, fields.get("description"), agenda,
                        durationMinutes, organizerEmail, requiredParticipants, optionalParticipants,
                        startDate, endDate, preferredStartTime, preferredTimeZone));
            } catch (IllegalArgumentException ex) {
                return clarification(ex.getMessage());
            }
        } catch (PromptProblem problem) {
            return clarification(problem.getMessage());
        }
    }

    private Map<String, String> parseFields(String prompt) {
        var fields = new LinkedHashMap<String, String>();
        for (String line : prompt.lines().toList()) {
            if (line.isBlank()) continue;
            int separator = line.indexOf(':');
            if (separator < 1) throw new PromptProblem("Each nonblank line must use `key: value` format.");
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!ALLOWED_FIELDS.contains(key)) throw new PromptProblem("Unrecognized field `" + key + "`.");
            if (fields.putIfAbsent(key, value) != null)
                throw new PromptProblem("Field `" + key + "` was supplied more than once; provide one value.");
        }
        return fields;
    }

    private List<String> splitAgenda(String value) {
        String[] items = value.split("\\|", -1);
        var agenda = new ArrayList<String>(items.length);
        for (String item : items) {
            if (item.isBlank()) throw new PromptProblem("Agenda items must be nonblank and separated by `|`.");
            agenda.add(item.trim());
        }
        return List.copyOf(agenda);
    }

    private int parseDuration(String value) {
        try {
            int duration = Integer.parseInt(value.trim());
            if (duration <= 0) throw new PromptProblem("durationMinutes must be a positive integer.");
            return duration;
        } catch (NumberFormatException ex) {
            throw new PromptProblem("durationMinutes must be a positive integer.");
        }
    }

    private String normalizeEmail(String value, String field) {
        try {
            return new EmailAddress(value).value();
        } catch (IllegalArgumentException ex) {
            throw new PromptProblem("`" + field + "` must contain a valid email address.");
        }
    }

    private List<String> parseEmails(String value, String field) {
        if (value == null || value.isBlank()) return List.of();
        String[] rawEmails = value.split(",", -1);
        var emails = new ArrayList<String>(rawEmails.length);
        var unique = new HashSet<String>();
        for (String rawEmail : rawEmails) {
            String email = normalizeEmail(rawEmail, field);
            if (!unique.add(email)) throw new PromptProblem("`" + field + "` contains a duplicate email address.");
            emails.add(email);
        }
        return List.copyOf(emails);
    }

    private LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new PromptProblem("`" + field + "` must use ISO date format YYYY-MM-DD.");
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.trim().matches("\\d{2}:\\d{2}"))
            throw new PromptProblem("`preferredStartTime` must use 24-hour HH:mm format.");
        try {
            return LocalTime.parse(value.trim(), TIME_FORMAT);
        } catch (DateTimeException ex) {
            throw new PromptProblem("`preferredStartTime` must use a valid 24-hour HH:mm time.");
        }
    }

    private ZoneId parseZone(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            ZoneId zone = ZoneId.of(value.trim());
            if (!ZoneId.getAvailableZoneIds().contains(zone.getId()))
                throw new DateTimeException("An IANA time-zone ID is required");
            return zone;
        } catch (DateTimeException ex) {
            throw new PromptProblem("`preferredTimeZone` must be a valid IANA time-zone ID.");
        }
    }

    private MeetingAiResult clarification(String message) {
        return MeetingAiResult.needsClarification(message);
    }

    private static final class PromptProblem extends RuntimeException {
        private PromptProblem(String message) { super(message); }
    }
}
