package com.example.scheduler.user.application;

import com.example.scheduler.shared.domain.DayWorkingHours;
import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.domain.TimeRange;
import com.example.scheduler.user.api.UserDtos;
import com.example.scheduler.user.domain.UserProfile;
import com.example.scheduler.user.domain.WorkingHours;
import com.example.scheduler.user.infrastructure.persistence.UserEntity;
import com.example.scheduler.user.infrastructure.persistence.UserWorkingHoursEntity;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserProfile toDomain(UserDtos.CreateUserRequest request) {
        var email = new EmailAddress(request.email());
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timeZone().trim());
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Time zone must be a valid IANA zone ID", exception);
        }
        return new UserProfile(email, request.displayName(), zone, toDomain(request.workingHours()));
    }

    public WorkingHours toDomain(List<UserDtos.WorkingHoursRequest> requests) {
        var entries = requests == null ? List.<UserDtos.WorkingHoursRequest>of() : requests;
        return new WorkingHours(entries.stream().map(request -> new DayWorkingHours(request.dayOfWeek(),
                new TimeRange(request.startTime(), request.endTime()))).toList());
    }

    public List<UserWorkingHoursEntity> toEntities(WorkingHours hours) {
        return hours.days().stream().map(day -> new UserWorkingHoursEntity(day.dayOfWeek(),
                day.hours().start(), day.hours().end())).toList();
    }

    public UserDtos.UserResponse toResponse(UserEntity entity) {
        var hours = entity.getWorkingHours().stream()
                .map(item -> new UserDtos.WorkingHoursRequest(item.getDayOfWeek(), item.getStartTime(), item.getEndTime()))
                .toList();
        return new UserDtos.UserResponse(entity.getId(), entity.getEmail(), entity.getDisplayName(),
                entity.getTimeZone(), hours, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
