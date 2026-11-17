package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.domain.FreeInterval;
import com.example.scheduler.shared.domain.InstantRange;
import java.util.List;

/** Sorted half-open interval normalization and intersection for required participant availability. */
public interface AvailabilityIntersectionService {
    List<InstantRange> normalize(List<FreeInterval> intervals);
    List<InstantRange> intersect(List<List<FreeInterval>> participantIntervals);
}
