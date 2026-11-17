package com.example.scheduler.calendar.domain;

import com.example.scheduler.shared.domain.FreeInterval;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Interval utilities over half-open instant ranges. */
@Component
public class IntervalOperations {
    /** Sorts and merges overlapping or adjacent busy intervals: O(n log n) time, O(n) space. */
    public List<BusyInterval> mergeBusyIntervals(List<BusyInterval> intervals) {
        if (intervals == null || intervals.isEmpty()) return List.of();
        var sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparing(BusyInterval::start).thenComparing(BusyInterval::end));
        var merged = new ArrayList<BusyInterval>();
        var currentStart = sorted.getFirst().start();
        var currentEnd = sorted.getFirst().end();
        for (int index = 1; index < sorted.size(); index++) {
            var next = sorted.get(index);
            if (!next.start().isAfter(currentEnd)) {
                if (next.end().isAfter(currentEnd)) currentEnd = next.end();
            } else {
                merged.add(new BusyInterval(currentStart, currentEnd));
                currentStart = next.start();
                currentEnd = next.end();
            }
        }
        merged.add(new BusyInterval(currentStart, currentEnd));
        return List.copyOf(merged);
    }

    /** Subtracts busy periods from working periods: O(w log w + b log b) time, O(w + b) space. */
    public List<FreeInterval> subtractBusyIntervals(List<FreeInterval> working, List<BusyInterval> busy) {
        if (working == null || working.isEmpty()) return List.of();
        var sortedWorking = new ArrayList<>(working);
        sortedWorking.sort(Comparator.comparing(FreeInterval::start));
        var mergedBusy = mergeBusyIntervals(busy);
        var result = new ArrayList<FreeInterval>();
        int busyIndex = 0;
        for (var window : sortedWorking) {
            Instant cursor = window.start();
            while (busyIndex < mergedBusy.size() && !mergedBusy.get(busyIndex).end().isAfter(cursor)) busyIndex++;
            int index = busyIndex;
            while (index < mergedBusy.size()) {
                var block = mergedBusy.get(index);
                if (!block.start().isBefore(window.end())) break;
                if (block.start().isAfter(cursor)) result.add(new FreeInterval(cursor, min(block.start(), window.end())));
                if (block.end().isAfter(cursor)) cursor = block.end();
                if (!cursor.isBefore(window.end())) break;
                index++;
            }
            if (cursor.isBefore(window.end())) result.add(new FreeInterval(cursor, window.end()));
            busyIndex = index;
        }
        return List.copyOf(result);
    }

    private Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
}
