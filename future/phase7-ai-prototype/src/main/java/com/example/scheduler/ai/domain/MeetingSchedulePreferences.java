package com.example.scheduler.ai.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Scheduling preferences passed to Meeting Service for backend enforcement; a null zone defaults to organizer local. */
public record MeetingSchedulePreferences(LocalDate preferredStartDate, LocalDate preferredEndDate,
                                         LocalTime preferredStartTime, ZoneId preferredTimeZone) {
    public MeetingSchedulePreferences(LocalDate preferredStartDate, LocalDate preferredEndDate,
                                      LocalTime preferredStartTime) {
        this(preferredStartDate, preferredEndDate, preferredStartTime, null);
    }
    public boolean isEmpty() {
        return preferredStartDate == null && preferredEndDate == null && preferredStartTime == null
                && preferredTimeZone == null;
    }
}
