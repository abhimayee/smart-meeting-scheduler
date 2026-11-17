package com.example.scheduler.user.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() { }
    public record WorkingHoursRequest(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) { }
    public record CreateUserRequest(@NotBlank @Email @Size(max = 320) String email,
                                    @NotBlank @Size(max = 200) String displayName,
                                    @NotBlank @Size(max = 100) String timeZone,
                                    @Valid List<WorkingHoursRequest> workingHours) { }
    public record UpdateWorkingHoursRequest(@NotNull List<@Valid WorkingHoursRequest> workingHours) { }
    public record UserResponse(UUID id, String email, String displayName, String timeZone,
                               List<WorkingHoursRequest> workingHours, java.time.Instant createdAt,
                               java.time.Instant updatedAt) { }
}
