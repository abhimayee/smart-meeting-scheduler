package com.example.scheduler.calendar.application;

import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventEntity;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adds repeatable busy-event examples for local availability demonstrations. */
@Component
@Profile("!prod & !production & !test")
public class DevelopmentCalendarDataSeeder implements ApplicationRunner {
    private final CalendarEventRepository eventRepository;

    public DevelopmentCalendarDataSeeder(CalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedBusyEvent("scheduler-demo-london@example.com", "Demo busy event — London",
                ZoneId.of("Europe/London"), LocalTime.of(10, 0));
        seedBusyEvent("scheduler-demo-new-york@example.com", "Demo busy event — New York",
                ZoneId.of("America/New_York"), LocalTime.of(14, 0));
        seedCancelledEvent("scheduler-demo-organizer@example.com", "Demo cancelled event — organizer",
                ZoneId.of("Asia/Kolkata"), LocalTime.of(11, 0));
    }

    private void seedBusyEvent(String email, String title, ZoneId zone, LocalTime localStart) {
        var date = nextMonday();
        var start = date.atTime(localStart).atZone(zone).toInstant();
        saveIfMissing(email, title + " " + date, start, start.plus(Duration.ofHours(1)), false);
    }

    private void seedCancelledEvent(String email, String title, ZoneId zone, LocalTime localStart) {
        var date = nextMonday();
        var start = date.atTime(localStart).atZone(zone).toInstant();
        saveIfMissing(email, title + " " + date, start, start.plus(Duration.ofHours(1)), true);
    }

    private LocalDate nextMonday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private void saveIfMissing(String email, String title, java.time.Instant start, java.time.Instant end,
                               boolean cancelled) {
        UUID id = UUID.nameUUIDFromBytes((email + "|" + title).getBytes(StandardCharsets.UTF_8));
        if (eventRepository.existsById(id)) return;
        var event = new CalendarEventEntity(id, email, title, start, end);
        if (cancelled) event.cancel();
        eventRepository.saveAndFlush(event);
    }
}
