package com.example.scheduler.user.domain;

import com.example.scheduler.shared.domain.DayWorkingHours;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record WorkingHours(List<DayWorkingHours> days) {
    public WorkingHours {
        days = days == null ? List.of() : List.copyOf(days);
        if (days.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Working-hour entries must not be null");
        var ordered = new ArrayList<>(days);
        ordered.sort(Comparator.comparing(DayWorkingHours::dayOfWeek)
                .thenComparing(day -> day.hours().start()));
        for (int index = 1; index < ordered.size(); index++) {
            var previous = ordered.get(index - 1);
            var current = ordered.get(index);
            if (previous.dayOfWeek() == current.dayOfWeek()
                    && current.hours().start().isBefore(previous.hours().end()))
                throw new IllegalArgumentException("Working-hour intervals for a day must not overlap");
        }
    }
}
