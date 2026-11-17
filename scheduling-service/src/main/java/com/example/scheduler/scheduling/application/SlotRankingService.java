package com.example.scheduler.scheduling.application;

import com.example.scheduler.scheduling.domain.ParticipantAvailability;
import com.example.scheduler.scheduling.domain.SlotSuggestion;
import com.example.scheduler.shared.domain.EmailAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SlotRankingService {
    public List<SlotSuggestion> rank(List<SlotSuggestion> suggestions, EmailAddress organizer, ZoneId organizerZone,
                                     LocalDate preferredDate, LocalTime preferredStartTime) {
        return rank(suggestions, organizer, organizerZone, null, preferredDate, preferredStartTime);
    }

    public List<SlotSuggestion> rank(List<SlotSuggestion> suggestions, EmailAddress organizer, ZoneId organizerZone,
                                     ZoneId preferenceZone, LocalDate preferredDate, LocalTime preferredStartTime) {
        Comparator<RankedSlot> comparator = Comparator.comparing((RankedSlot ranked) -> ranked.slot().requiredParticipantsAvailable()).reversed()
                .thenComparing(Comparator.comparingInt((RankedSlot ranked) -> ranked.slot().optionalParticipantsAvailable()).reversed())
                .thenComparingInt(RankedSlot::preferredDateMismatch)
                .thenComparingLong(RankedSlot::preferredTimeDistance)
                .thenComparingLong(RankedSlot::localMiddayDistance)
                .thenComparing(RankedSlot::start);
        var ranked = suggestions.stream().map(slot -> toRankedSlot(slot, organizer, organizerZone, preferenceZone,
                        preferredDate, preferredStartTime))
                .sorted(comparator).map(RankedSlot::slot).toList();
        return List.copyOf(ranked);
    }

    private RankedSlot toRankedSlot(SlotSuggestion slot, EmailAddress organizer, ZoneId organizerZone,
                                   ZoneId preferenceZone, LocalDate preferredDate, LocalTime preferredStartTime) {
        var localStart = slot.interval().start().atZone(preferenceZone == null ? organizerZone : preferenceZone);
        int dateMismatch = preferredDate == null || localStart.toLocalDate().equals(preferredDate) ? 0 : 1;
        long timeDistance = preferredStartTime == null ? 0 : timeDistanceMinutes(localStart.toLocalTime(), preferredStartTime);
        return new RankedSlot(slot, dateMismatch, timeDistance, localMiddayDistance(slot.participants()), slot.interval().start());
    }

    private long timeDistanceMinutes(LocalTime actual, LocalTime preferred) {
        long distance = Math.abs(Duration.between(preferred, actual).toMinutes());
        return Math.min(distance, 1440 - distance);
    }

    /** Preference tie-break: minimize total distance of available attendees' local starts from local noon. */
    private long localMiddayDistance(List<ParticipantAvailability> participants) {
        long distance = 0;
        for (var participant : participants) {
            if (participant.available())
                distance += Math.abs(Duration.between(LocalTime.NOON, participant.localStart().toLocalTime()).toMinutes());
        }
        return distance;
    }

    private record RankedSlot(SlotSuggestion slot, int preferredDateMismatch, long preferredTimeDistance,
                              long localMiddayDistance, java.time.Instant start) { }
}
