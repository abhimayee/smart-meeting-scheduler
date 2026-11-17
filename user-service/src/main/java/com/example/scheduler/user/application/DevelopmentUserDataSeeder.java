package com.example.scheduler.user.application;

import com.example.scheduler.user.api.UserDtos;
import com.example.scheduler.user.infrastructure.persistence.UserRepository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adds stable local demo profiles without replacing user-managed data. */
@Component
@Profile("!prod & !production & !test")
public class DevelopmentUserDataSeeder implements ApplicationRunner {
    private final UserRepository userRepository;
    private final UserService userService;

    public DevelopmentUserDataSeeder(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("scheduler-demo-organizer@example.com", "Demo Organizer", "Asia/Kolkata",
                weekdayHours(LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(13, 0), LocalTime.of(17, 0)));
        seed("scheduler-demo-london@example.com", "London Participant", "Europe/London",
                selectedDays(List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY), LocalTime.of(8, 30), LocalTime.of(16, 30)));
        seed("scheduler-demo-new-york@example.com", "New York Participant", "America/New_York",
                selectedDays(List.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(17, 0)));
        seed("scheduler-demo-singapore@example.com", "Singapore Participant", "Asia/Singapore",
                selectedDays(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        LocalTime.of(10, 0), LocalTime.of(18, 0)));
    }

    private void seed(String email, String name, String zone, List<UserDtos.WorkingHoursRequest> hours) {
        if (!userRepository.existsByEmail(email)) {
            userService.create(new UserDtos.CreateUserRequest(email, name, zone, hours));
        }
    }

    private List<UserDtos.WorkingHoursRequest> weekdayHours(LocalTime morningStart, LocalTime morningEnd,
                                                              LocalTime afternoonStart, LocalTime afternoonEnd) {
        var hours = new ArrayList<UserDtos.WorkingHoursRequest>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            hours.add(new UserDtos.WorkingHoursRequest(day, morningStart, morningEnd));
            hours.add(new UserDtos.WorkingHoursRequest(day, afternoonStart, afternoonEnd));
        }
        return List.copyOf(hours);
    }

    private List<UserDtos.WorkingHoursRequest> selectedDays(List<DayOfWeek> days, LocalTime start, LocalTime end) {
        return days.stream().map(day -> new UserDtos.WorkingHoursRequest(day, start, end)).toList();
    }
}
