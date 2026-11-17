package com.example.scheduler.scheduling.api;

import com.example.scheduler.shared.domain.ParticipantType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

public final class SchedulingDtos {
    private SchedulingDtos() { }
    public record ParticipantRequest(@NotBlank @Email String email, @NotNull ParticipantType type) { }
    public record FindSlotsRequest(@NotBlank @Email String organizer, @Valid List<ParticipantRequest> participants,
                                   @Positive int durationMinutes, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                                   LocalDate preferredDate, LocalTime preferredStartTime, String preferredTimeZone) {
        public FindSlotsRequest(String organizer, List<ParticipantRequest> participants, int durationMinutes,
                                LocalDate startDate, LocalDate endDate, LocalDate preferredDate,
                                LocalTime preferredStartTime) {
            this(organizer, participants, durationMinutes, startDate, endDate, preferredDate, preferredStartTime, null);
        }
        @AssertTrue(message = "preferredTimeZone must be a valid IANA time-zone ID")
        public boolean isPreferredTimeZoneValid() {
            if (preferredTimeZone == null || preferredTimeZone.isBlank()) return true;
            try { return ZoneId.getAvailableZoneIds().contains(ZoneId.of(preferredTimeZone.trim()).getId()); }
            catch (DateTimeException ex) { return false; }
        }
    }
    public record ParticipantSlotResponse(String email, ParticipantType type, boolean available,
                                          ZonedDateTime localStart, ZonedDateTime localEnd) { }
    public record SlotSuggestionResponse(Instant start, Instant end, boolean requiredParticipantsAvailable,
                                         int optionalParticipantsAvailable, int optionalParticipantsTotal,
                                         List<ParticipantSlotResponse> participants) { }
    public record FindSlotsResponse(List<SlotSuggestionResponse> suggestions) { }
}
