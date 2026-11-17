package com.example.scheduler.shared.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalTime;
import static org.junit.jupiter.api.Assertions.*;

class TimeRangeTest {
    @Test void acceptsSameDayIncreasingRange() { assertDoesNotThrow(() -> new TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 0))); }
    @Test void rejectsZeroLengthAndOvernightRanges() {
        assertThrows(IllegalArgumentException.class, () -> new TimeRange(LocalTime.NOON, LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> new TimeRange(LocalTime.of(22, 0), LocalTime.of(6, 0)));
    }
}
