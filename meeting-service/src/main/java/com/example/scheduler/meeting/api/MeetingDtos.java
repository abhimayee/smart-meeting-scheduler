package com.example.scheduler.meeting.api;

import com.example.scheduler.meeting.domain.MeetingStatus;
import com.example.scheduler.meeting.domain.MeetingType;
import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.meeting.domain.ResponseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.List;
import java.util.UUID;

public final class MeetingDtos {
    private MeetingDtos() { }
    public record CreateMeetingRequest(@NotBlank @Size(max = 300) String title, @NotBlank String agenda,
                                       @Positive int durationMinutes, @NotBlank @Email @Size(max = 320) String organizerEmail,
                                       List<@NotBlank @Email @Size(max = 320) String> requiredParticipants,
                                       List<@NotBlank @Email @Size(max = 320) String> optionalParticipants,
                                       MeetingType meetingType) {
        public CreateMeetingRequest(String title, String agenda, int durationMinutes, String organizerEmail,
                                    List<String> requiredParticipants, List<String> optionalParticipants) {
            this(title, agenda, durationMinutes, organizerEmail, requiredParticipants, optionalParticipants, null);
        }
    }
    public record ParticipantResponse(UUID id, UUID meetingId, String email, ParticipantType type,
                                      ResponseStatus responseStatus) { }
    public record MeetingResponse(UUID id, String title, String agenda, int durationMinutes,
                                  String organizerEmail, MeetingStatus status, Instant createdAt,
                                  Instant updatedAt, Instant confirmedStart, Instant confirmedEnd,
                                  List<ParticipantResponse> participants) { }
    public record ConfirmMeetingRequest(@NotNull Instant start, @NotNull Instant end) { }
    public record SchedulingParticipantRequest(String email, ParticipantType type) { }
    public record FindSlotsRequest(String organizer, List<SchedulingParticipantRequest> participants,
                                   int durationMinutes, LocalDate startDate, LocalDate endDate,
                                   LocalDate preferredDate, LocalTime preferredStartTime, String preferredTimeZone) {
        public FindSlotsRequest(String organizer, List<SchedulingParticipantRequest> participants,
                                int durationMinutes, LocalDate startDate, LocalDate endDate,
                                LocalDate preferredDate, LocalTime preferredStartTime) {
            this(organizer, participants, durationMinutes, startDate, endDate, preferredDate, preferredStartTime, null);
        }
    }
    /** Preference dates and times default to the organizer's IANA time zone; an optional zone can override it. */
    public record ScheduleMeetingRequest(LocalDate preferredStartDate, LocalDate preferredEndDate,
                                         LocalTime preferredStartTime, String preferredTimeZone) {
        public ScheduleMeetingRequest(LocalDate preferredStartDate, LocalDate preferredEndDate,
                                      LocalTime preferredStartTime) {
            this(preferredStartDate, preferredEndDate, preferredStartTime, null);
        }
        @AssertTrue(message = "Preference dates must be supplied together and in ascending order; preferredTimeZone must be a valid IANA ID")
        public boolean isPreferenceRangeValid() {
            if ((preferredStartDate == null) != (preferredEndDate == null)
                    || (preferredStartDate != null && preferredEndDate.isBefore(preferredStartDate))) return false;
            if (preferredTimeZone == null || preferredTimeZone.isBlank()) return true;
            try {
                ZoneId zone = ZoneId.of(preferredTimeZone.trim());
                return ZoneId.getAvailableZoneIds().contains(zone.getId());
            } catch (DateTimeException ex) { return false; }
        }

        public void validate() {
            if (!isPreferenceRangeValid())
                throw new IllegalArgumentException("Preference dates must be supplied together and in ascending order, and preferredTimeZone must be a valid IANA ID");
        }
    }
    public record ScheduleMeetingResponse(UUID meetingId, List<SlotSuggestionResponse> suggestions) { }
    public record SlotSuggestionResponse(Instant start, Instant end, boolean requiredParticipantsAvailable,
                                         int optionalParticipantsAvailable, int optionalParticipantsTotal,
                                         List<ParticipantSlotResponse> participants) { }
    public record ParticipantSlotResponse(String email, ParticipantType type, boolean available,
                                          String localStart, String localEnd) { }
}
