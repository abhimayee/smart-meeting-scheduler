package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.domain.InstantRange;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlotGenerationService {
    private final int granularityMinutes;

    public SlotGenerationService(@Value("${scheduler.slot-granularity-minutes:15}") int granularityMinutes) {
        if (granularityMinutes <= 0 || granularityMinutes > 1440)
            throw new IllegalArgumentException("Slot granularity must be between 1 and 1440 minutes");
        this.granularityMinutes = granularityMinutes;
    }

    public List<InstantRange> generate(List<InstantRange> commonAvailability, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative())
            throw new IllegalArgumentException("Meeting duration must be positive");
        long stepSeconds = Math.multiplyExact(granularityMinutes, 60L);
        var candidates = new ArrayList<InstantRange>();
        for (var interval : commonAvailability) {
            Instant start = alignUp(interval.start(), stepSeconds);
            while (!start.plus(duration).isAfter(interval.end())) {
                candidates.add(new InstantRange(start, start.plus(duration)));
                start = start.plusSeconds(stepSeconds);
            }
        }
        return List.copyOf(candidates);
    }

    public int granularityMinutes() { return granularityMinutes; }

    private Instant alignUp(Instant value, long stepSeconds) {
        long alignedSecond = Math.multiplyExact(Math.floorDiv(value.getEpochSecond(), stepSeconds), stepSeconds);
        Instant aligned = Instant.ofEpochSecond(alignedSecond);
        return aligned.isBefore(value) ? aligned.plusSeconds(stepSeconds) : aligned;
    }
}
