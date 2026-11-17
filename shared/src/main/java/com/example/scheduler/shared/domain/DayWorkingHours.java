package com.example.scheduler.shared.domain;

import java.time.DayOfWeek;

public record DayWorkingHours(DayOfWeek dayOfWeek, TimeRange hours) {
    public DayWorkingHours {
        if (dayOfWeek == null || hours == null) throw new IllegalArgumentException("Day and working hours are required");
    }
}
