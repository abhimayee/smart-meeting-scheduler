package com.example.scheduler.meeting.api;

import com.example.scheduler.meeting.application.MeetingService;
import com.example.scheduler.meeting.domain.MeetingStatus;
import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.meeting.domain.ResponseStatus;
import com.example.scheduler.shared.exception.GlobalExceptionHandler;
import com.example.scheduler.shared.exception.ConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeetingController.class)
@Import(GlobalExceptionHandler.class)
class MeetingControllerTest {
    @Autowired MockMvc mvc;
    @MockBean MeetingService meetingService;

    private MeetingDtos.MeetingResponse response(UUID id, MeetingStatus meetingStatus) {
        var participant = new MeetingDtos.ParticipantResponse(UUID.randomUUID(), id,
                "john@example.com", ParticipantType.REQUIRED, ResponseStatus.PENDING);
        return new MeetingDtos.MeetingResponse(id, "Architecture Review", "API boundaries", 60,
                "organizer@example.com", meetingStatus, Instant.parse("2026-10-01T10:00:00Z"),
                Instant.parse("2026-10-01T10:00:00Z"),
                meetingStatus == MeetingStatus.CONFIRMED ? Instant.parse("2026-10-05T10:00:00Z") : null,
                meetingStatus == MeetingStatus.CONFIRMED ? Instant.parse("2026-10-05T11:00:00Z") : null,
                List.of(participant));
    }

    @Test void createsMeetingRequest() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.create(any())).thenReturn(response(id, MeetingStatus.REQUESTED));
        mvc.perform(post("/api/meetings").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Architecture Review","agenda":"API boundaries","durationMinutes":60,
                 "organizerEmail":"organizer@example.com","requiredParticipants":["john@example.com"],
                 "optionalParticipants":[]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.participants[0].email").value("john@example.com"));
    }

    @Test void rejectsInvalidEmailAndNonPositiveDuration() throws Exception {
        mvc.perform(post("/api/meetings").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Review","agenda":"Agenda","durationMinutes":0,
                 "organizerEmail":"not-an-email","requiredParticipants":["bad-email"]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists())
                .andExpect(jsonPath("$.fieldErrors.organizerEmail").exists());
    }

    @Test void getsMeetingAndParticipantList() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.get(id)).thenReturn(response(id, MeetingStatus.REQUESTED));
        mvc.perform(get("/api/meetings/{meetingId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerEmail").value("organizer@example.com"));
        when(meetingService.getParticipants(id)).thenReturn(response(id, MeetingStatus.REQUESTED).participants());
        mvc.perform(get("/api/meetings/{meetingId}/participants", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REQUIRED"));
    }

    @Test void scheduleEndpointReturnsSuggestionsWithoutRequestBody() throws Exception {
        var id = UUID.randomUUID();
        var slot = new MeetingDtos.SlotSuggestionResponse(Instant.parse("2026-10-12T10:00:00Z"),
                Instant.parse("2026-10-12T10:30:00Z"), true, 0, 1, List.of());
        when(meetingService.schedule(id)).thenReturn(new MeetingDtos.ScheduleMeetingResponse(id, List.of(slot)));

        mvc.perform(post("/api/meetings/{meetingId}/schedule", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingId").value(id.toString()))
                .andExpect(jsonPath("$.suggestions[0].start").value("2026-10-12T10:00:00Z"));
        verify(meetingService).schedule(id);
    }

    @Test void scheduleEndpointReturnsConflictForMeetingInWrongState() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.schedule(id)).thenThrow(new ConflictException("Wrong state"));
        mvc.perform(post("/api/meetings/{meetingId}/schedule", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with the current resource state"));
    }

    @Test void scheduleEndpointAcceptsOrganizerLocalDateAndTimePreferences() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.schedule(eq(id), any())).thenReturn(new MeetingDtos.ScheduleMeetingResponse(id, List.of()));

        mvc.perform(post("/api/meetings/{meetingId}/schedule", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"preferredStartDate":"2026-11-02","preferredEndDate":"2026-11-04","preferredStartTime":"10:15"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isEmpty());

        verify(meetingService).schedule(id, new MeetingDtos.ScheduleMeetingRequest(
                LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04"), LocalTime.of(10, 15)));
    }

    @Test void scheduleEndpointAcceptsExplicitPreferenceTimeZone() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.schedule(eq(id), any())).thenReturn(new MeetingDtos.ScheduleMeetingResponse(id, List.of()));

        mvc.perform(post("/api/meetings/{meetingId}/schedule", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"preferredStartDate":"2026-11-02","preferredEndDate":"2026-11-04",
                 "preferredStartTime":"10:15","preferredTimeZone":"America/New_York"}
                """))
                .andExpect(status().isOk());

        verify(meetingService).schedule(id, new MeetingDtos.ScheduleMeetingRequest(
                LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04"), LocalTime.of(10, 15), "America/New_York"));
    }

    @Test void scheduleEndpointRejectsInvalidPreferenceTimeZone() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(post("/api/meetings/{meetingId}/schedule", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"preferredTimeZone":"Not/A_Zone"}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(meetingService);
    }

    @Test void rejectsIncompleteOrReversedPreferredDateRange() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(post("/api/meetings/{meetingId}/schedule", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"preferredStartDate":"2026-11-04","preferredEndDate":"2026-11-02"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"));
        mvc.perform(post("/api/meetings/{meetingId}/schedule", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"preferredStartDate":"2026-11-02"}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(meetingService);
    }

    @Test void updatesMeetingThroughService() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.update(eq(id), any())).thenReturn(response(id, MeetingStatus.REQUESTED));
        mvc.perform(put("/api/meetings/{meetingId}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Architecture Review","agenda":"API boundaries","durationMinutes":60,
                 "organizerEmail":"organizer@example.com","requiredParticipants":["john@example.com"],
                 "optionalParticipants":[]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
        verify(meetingService).update(eq(id), any());
    }

    @Test void rejectsInvalidUpdateRequestAndKeepsUnexpectedErrorSafe() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(put("/api/meetings/{meetingId}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Architecture Review","agenda":"API boundaries","durationMinutes":0,
                 "organizerEmail":"organizer@example.com","requiredParticipants":[],"optionalParticipants":[]}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(meetingService);

        when(meetingService.update(eq(id), any())).thenThrow(new IllegalStateException("sensitive internal detail"));
        mvc.perform(put("/api/meetings/{meetingId}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Architecture Review","agenda":"API boundaries","durationMinutes":30,
                 "organizerEmail":"organizer@example.com","requiredParticipants":[],"optionalParticipants":[]}
                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    }

    @Test void preservesConflictResponseForDatabaseIntegrityViolations() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.update(eq(id), any())).thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        mvc.perform(put("/api/meetings/{meetingId}", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Architecture Review","agenda":"API boundaries","durationMinutes":30,
                 "organizerEmail":"organizer@example.com","requiredParticipants":["john@example.com"],
                 "optionalParticipants":[]}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data"));
    }

    @Test void confirmsAndCancelsMeetingThroughService() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.confirm(eq(id), any())).thenReturn(response(id, MeetingStatus.CONFIRMED));
        mvc.perform(post("/api/meetings/{meetingId}/confirm", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"start":"2026-10-05T10:00:00Z","end":"2026-10-05T11:00:00Z"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        when(meetingService.cancel(id)).thenReturn(response(id, MeetingStatus.CANCELLED));
        mvc.perform(post("/api/meetings/{meetingId}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(meetingService).cancel(id);
    }

    @Test void returnsConflictWhenConfirmationSlotIsUnavailable() throws Exception {
        var id = UUID.randomUUID();
        when(meetingService.confirm(eq(id), any())).thenThrow(new ConflictException("Selected meeting slot is no longer available"));

        mvc.perform(post("/api/meetings/{meetingId}/confirm", id).contentType(MediaType.APPLICATION_JSON).content("""
                {"start":"2026-10-12T10:00:00Z","end":"2026-10-12T10:30:00Z"}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with the current resource state"));
    }
}
