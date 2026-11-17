package com.example.scheduler.scheduling.api;

import com.example.scheduler.shared.domain.ParticipantType;
import com.example.scheduler.scheduling.application.SchedulingService;
import com.example.scheduler.scheduling.domain.ParticipantAvailability;
import com.example.scheduler.scheduling.domain.SlotSuggestion;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.InstantRange;
import com.example.scheduler.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SchedulingController.class)
@Import(GlobalExceptionHandler.class)
class SchedulingControllerTest {
    @Autowired MockMvc mvc;
    @MockBean SchedulingService schedulingService;

    @Test void acceptsSchedulingRequestAndReturnsUtcAndLocalSuggestionDetails() throws Exception {
        var start = Instant.parse("2026-10-05T13:30:00Z");
        var end = Instant.parse("2026-10-05T14:30:00Z");
        var attendee = new ParticipantAvailability(new EmailAddress("abhipsa@example.com"), ParticipantType.REQUIRED, true,
                start.atZone(ZoneId.of("Asia/Kolkata")), end.atZone(ZoneId.of("Asia/Kolkata")));
        when(schedulingService.findSlots(any())).thenReturn(List.of(new SlotSuggestion(new InstantRange(start, end), true, 0, 0, List.of(attendee))));
        mvc.perform(post("/api/scheduling/find-slots").contentType(MediaType.APPLICATION_JSON).content("""
                {"organizer":"abhipsa@example.com","participants":[],"durationMinutes":60,
                 "startDate":"2026-10-05","endDate":"2026-10-05","preferredStartTime":"10:00"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].start").value("2026-10-05T13:30:00Z"))
                .andExpect(jsonPath("$.suggestions[0].requiredParticipantsAvailable").value(true))
                .andExpect(jsonPath("$.suggestions[0].participants[0].localStart").exists());
    }

    @Test void rejectsInvalidParticipantEmailAndNonPositiveDuration() throws Exception {
        mvc.perform(post("/api/scheduling/find-slots").contentType(MediaType.APPLICATION_JSON).content("""
                {"organizer":"bad-email","participants":[],"durationMinutes":0,
                 "startDate":"2026-10-05","endDate":"2026-10-05"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.organizer").exists())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());
    }

    @Test void rejectsMissingDateAndInvalidParticipantType() throws Exception {
        mvc.perform(post("/api/scheduling/find-slots").contentType(MediaType.APPLICATION_JSON).content("""
                {"organizer":"abhipsa@example.com","participants":[{"email":"john@example.com","type":"GUEST"}],
                 "durationMinutes":60,"startDate":"2026-10-05"}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectsInvalidPreferenceTimeZone() throws Exception {
        mvc.perform(post("/api/scheduling/find-slots").contentType(MediaType.APPLICATION_JSON).content("""
                {"organizer":"abhipsa@example.com","participants":[],"durationMinutes":30,
                 "startDate":"2026-10-05","endDate":"2026-10-05","preferredTimeZone":"Not/A_Zone"}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedulingService);
    }
}
