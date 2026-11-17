package com.example.scheduler.calendar.api;

import com.example.scheduler.calendar.application.CalendarService;
import com.example.scheduler.calendar.application.UserAvailabilityService;
import com.example.scheduler.calendar.domain.AvailabilityWindow;
import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.shared.domain.FreeInterval;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.exception.GlobalExceptionHandler;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
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
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarController.class)
@Import(GlobalExceptionHandler.class)
class CalendarControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CalendarService calendarService;
    @MockBean UserAvailabilityService availabilityService;

    @Test void createsCalendarEvent() throws Exception {
        var id = UUID.randomUUID();
        when(calendarService.create(any())).thenReturn(new CalendarDtos.CalendarEventResponse(id, "a@example.com", "Review",
                Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T10:00:00Z"), CalendarEventStatus.BUSY,
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")));
        mvc.perform(post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"userEmail":"a@example.com","title":"Review","startAt":"2026-10-05T09:00:00Z","endAt":"2026-10-05T10:00:00Z"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("BUSY"));
    }

    @Test void rejectsMalformedInstantsAndMissingRequiredFields() throws Exception {
        mvc.perform(post("/api/calendar/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"userEmail":"a@example.com","startAt":"not-an-instant","endAt":"2026-10-05T10:00:00Z"}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test void getsEventByIdAndSoftDeletes() throws Exception {
        var id = UUID.randomUUID();
        when(calendarService.get(id)).thenReturn(new CalendarDtos.CalendarEventResponse(id, "a@example.com", "Review",
                Instant.parse("2026-10-05T09:00:00Z"), Instant.parse("2026-10-05T10:00:00Z"), CalendarEventStatus.BUSY, null, null));
        mvc.perform(get("/api/calendar/events/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BUSY"));
        mvc.perform(delete("/api/calendar/events/" + id)).andExpect(status().isNoContent());
        verify(calendarService).cancel(id);
    }

    @Test void listsEventsWithOptionalDateRange() throws Exception {
        when(calendarService.getByUser(eq("a@example.com"), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/calendar/users/a@example.com/events")
                        .param("start", "2026-10-05T00:00:00Z").param("end", "2026-10-06T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void returnsAvailabilityInUtcWithUserZone() throws Exception {
        var start = Instant.parse("2026-10-05T00:00:00Z");
        var end = Instant.parse("2026-10-06T00:00:00Z");
        when(availabilityService.getAvailability("a@example.com", start, end)).thenReturn(new AvailabilityWindow(
                new EmailAddress("a@example.com"), ZoneId.of("Asia/Kolkata"), start, end,
                List.of(new FreeInterval(start.plusSeconds(19800), start.plusSeconds(48600)))));
        mvc.perform(get("/api/calendar/users/a@example.com/availability").param("start", start.toString()).param("end", end.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.freeIntervals.length()").value(1));
    }

    @Test void missingEventIsNotFoundAndBadQueryTimeIsBadRequest() throws Exception {
        var id = UUID.randomUUID();
        when(calendarService.get(id)).thenThrow(new ResourceNotFoundException("Calendar event was not found"));
        mvc.perform(get("/api/calendar/events/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/calendar/users/a@example.com/events").param("start", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
