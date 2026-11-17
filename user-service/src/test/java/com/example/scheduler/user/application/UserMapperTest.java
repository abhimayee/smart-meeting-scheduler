package com.example.scheduler.user.application;

import com.example.scheduler.shared.domain.DayWorkingHours;
import com.example.scheduler.shared.domain.TimeRange;
import com.example.scheduler.user.api.UserDtos;
import com.example.scheduler.user.domain.WorkingHours;
import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {
    private final UserMapper mapper = new UserMapper();

    @Test void mapsValidIanaZoneAndNormalizesEmail() {
        var profile = mapper.toDomain(new UserDtos.CreateUserRequest(" ABHIPSA@Example.com ", "Abhipsa", "Asia/Kolkata", List.of()));
        assertEquals("abhipsa@example.com", profile.email().value());
        assertEquals("Asia/Kolkata", profile.timeZone().getId());
    }

    @Test void rejectsInvalidTimeZone() {
        var request = new UserDtos.CreateUserRequest("a@example.com", "Abhipsa", "Not/AZone", List.of());
        var error = assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(request));
        assertTrue(error.getMessage().contains("IANA"));
    }

    @Test void supportsMultipleNonOverlappingIntervalsOnOneDay() {
        var hours = mapper.toDomain(List.of(
                new UserDtos.WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.NOON),
                new UserDtos.WorkingHoursRequest(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(17, 0))));
        assertEquals(2, hours.days().size());
    }

    @Test void rejectsInvalidAndOverlappingWorkingHourRanges() {
        assertThrows(IllegalArgumentException.class, () -> new TimeRange(LocalTime.of(17, 0), LocalTime.of(9, 0)));
        assertThrows(IllegalArgumentException.class, () -> new WorkingHours(List.of(
                new DayWorkingHours(DayOfWeek.MONDAY, new TimeRange(LocalTime.of(9, 0), LocalTime.of(12, 0))),
                new DayWorkingHours(DayOfWeek.MONDAY, new TimeRange(LocalTime.of(11, 30), LocalTime.of(13, 0))))));
    }
}
