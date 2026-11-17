package com.example.scheduler.user.api;

import com.example.scheduler.user.application.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockBean UserService userService;

    @Test void createEndpointValidatesAndReturnsUserDto() throws Exception {
        when(userService.create(any())).thenReturn(new UserDtos.UserResponse(UUID.randomUUID(), "abhipsa@example.com", "Abhipsa", "Asia/Kolkata", List.of(), Instant.now(), Instant.now()));
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"abhipsa@example.com","displayName":"Abhipsa","timeZone":"Asia/Kolkata"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("abhipsa@example.com"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Kolkata"));
        verify(userService).create(any());
    }

    @Test void rejectsMalformedEmailAtApiBoundary() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"displayName\":\"A\",\"timeZone\":\"UTC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test void updatesMultipleWeeklyIntervals() throws Exception {
        when(userService.updateWorkingHours(any(), any())).thenReturn(new UserDtos.UserResponse(UUID.randomUUID(), "abhipsa@example.com", "Abhipsa", "Asia/Kolkata", List.of(), Instant.now(), Instant.now()));
        mvc.perform(put("/api/users/abhipsa@example.com/working-hours").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workingHours":[
                                  {"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"12:00"},
                                  {"dayOfWeek":"MONDAY","startTime":"13:00","endTime":"17:00"}
                                ]}
                                """))
                .andExpect(status().isOk());
        verify(userService).updateWorkingHours(any(), any());
    }
}
