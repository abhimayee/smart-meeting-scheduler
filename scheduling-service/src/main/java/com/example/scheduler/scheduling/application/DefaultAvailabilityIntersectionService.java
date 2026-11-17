package com.example.scheduler.scheduling.application;

import com.example.scheduler.shared.domain.FreeInterval;
import com.example.scheduler.shared.domain.InstantRange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DefaultAvailabilityIntersectionService implements AvailabilityIntersectionService {
    @Override
    public List<InstantRange> normalize(List<FreeInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) return List.of();
        var sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(FreeInterval::start).thenComparing(FreeInterval::end));
        var result = new ArrayList<InstantRange>();
        Instant start = sorted.getFirst().start();
        Instant end = sorted.getFirst().end();
        for (int index = 1; index < sorted.size(); index++) {
            var next = sorted.get(index);
            if (!next.start().isAfter(end)) {
                if (next.end().isAfter(end)) end = next.end();
            } else {
                result.add(new InstantRange(start, end));
                start = next.start();
                end = next.end();
            }
        }
        result.add(new InstantRange(start, end));
        return List.copyOf(result);
    }

    @Override
    public List<InstantRange> intersect(List<List<FreeInterval>> participantIntervals) {
        if (participantIntervals == null || participantIntervals.isEmpty())
            throw new IllegalArgumentException("At least one required participant is needed");
        var common = new ArrayList<>(normalize(participantIntervals.getFirst()));
        for (int participantIndex = 1; participantIndex < participantIntervals.size() && !common.isEmpty(); participantIndex++) {
            var nextParticipant = normalize(participantIntervals.get(participantIndex));
            common = intersectSorted(common, nextParticipant);
        }
        return List.copyOf(common);
    }

    private ArrayList<InstantRange> intersectSorted(List<InstantRange> left, List<InstantRange> right) {
        var result = new ArrayList<InstantRange>();
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.size() && rightIndex < right.size()) {
            var first = left.get(leftIndex);
            var second = right.get(rightIndex);
            Instant overlapStart = first.start().isAfter(second.start()) ? first.start() : second.start();
            Instant overlapEnd = first.end().isBefore(second.end()) ? first.end() : second.end();
            if (overlapStart.isBefore(overlapEnd)) result.add(new InstantRange(overlapStart, overlapEnd));
            if (first.end().isBefore(second.end())) leftIndex++;
            else if (second.end().isBefore(first.end())) rightIndex++;
            else { leftIndex++; rightIndex++; }
        }
        return coalesceAdjacent(result);
    }

    private ArrayList<InstantRange> coalesceAdjacent(List<InstantRange> ranges) {
        var result = new ArrayList<InstantRange>();
        if (ranges.isEmpty()) return result;
        Instant start = ranges.getFirst().start();
        Instant end = ranges.getFirst().end();
        for (int index = 1; index < ranges.size(); index++) {
            var next = ranges.get(index);
            if (!next.start().isAfter(end)) {
                if (next.end().isAfter(end)) end = next.end();
            } else {
                result.add(new InstantRange(start, end));
                start = next.start(); end = next.end();
            }
        }
        result.add(new InstantRange(start, end));
        return result;
    }
}
