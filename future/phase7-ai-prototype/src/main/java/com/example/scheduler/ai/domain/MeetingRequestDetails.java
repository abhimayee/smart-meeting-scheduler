package com.example.scheduler.ai.domain;

import com.example.scheduler.shared.domain.EmailAddress;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** AI-owned structured result; it is intentionally independent of Meeting Service's domain model. */
public record MeetingRequestDetails(String title, String description, List<String> agenda, int durationMinutes,
                                    String organizerEmail, List<String> requiredParticipants,
                                    List<String> optionalParticipants, LocalDate preferredStartDate,
                                    LocalDate preferredEndDate, LocalTime preferredStartTime,
                                    ZoneId preferredTimeZone) {
    public MeetingRequestDetails(String title, String description, List<String> agenda, int durationMinutes,
                                 String organizerEmail, List<String> requiredParticipants,
                                 List<String> optionalParticipants, LocalDate preferredStartDate,
                                 LocalDate preferredEndDate, LocalTime preferredStartTime) {
        this(title, description, agenda, durationMinutes, organizerEmail, requiredParticipants,
                optionalParticipants, preferredStartDate, preferredEndDate, preferredStartTime, null);
    }
    public MeetingRequestDetails {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        title = title.trim();
        if (title.length() > 300) throw new IllegalArgumentException("title must not exceed 300 characters");
        if (description != null) description = description.trim();
        if (durationMinutes <= 0) throw new IllegalArgumentException("durationMinutes must be positive");
        if (agenda == null || agenda.isEmpty()) throw new IllegalArgumentException("agenda must contain at least one item");
        agenda = agenda.stream().map(item -> {
            if (item == null || item.isBlank()) throw new IllegalArgumentException("agenda items must not be blank");
            return item.trim();
        }).toList();
        organizerEmail = new EmailAddress(organizerEmail).value();
        requiredParticipants = normalize(requiredParticipants);
        optionalParticipants = normalize(optionalParticipants);
        var uniqueEmails = new HashSet<String>();
        uniqueEmails.add(organizerEmail);
        var duplicates = new ArrayList<String>();
        for (String email : requiredParticipants) if (!uniqueEmails.add(email)) duplicates.add(email);
        for (String email : optionalParticipants) if (!uniqueEmails.add(email)) duplicates.add(email);
        if (!duplicates.isEmpty()) throw new IllegalArgumentException("participant emails must be unique and must not include the organizer");
        if ((preferredStartDate == null) != (preferredEndDate == null))
            throw new IllegalArgumentException("preferredStartDate and preferredEndDate must be supplied together");
        if (preferredStartDate != null && preferredEndDate.isBefore(preferredStartDate))
            throw new IllegalArgumentException("preferredEndDate must not precede preferredStartDate");
    }

    private static List<String> normalize(List<String> emails) {
        if (emails == null) return List.of();
        return emails.stream().map(email -> new EmailAddress(email).value()).toList();
    }
}
