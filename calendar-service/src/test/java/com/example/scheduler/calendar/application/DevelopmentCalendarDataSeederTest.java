package com.example.scheduler.calendar.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.scheduler.calendar.domain.CalendarEventStatus;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventEntity;
import com.example.scheduler.calendar.infrastructure.persistence.CalendarEventRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

class DevelopmentCalendarDataSeederTest {
    @Test
    void repeatedStartupAddsOnlyMissingStableDemoEvents() {
        CalendarEventRepository repository = mock(CalendarEventRepository.class);
        Set<UUID> storedIds = new HashSet<>();
        when(repository.existsById(any())).thenAnswer(call -> storedIds.contains(call.getArgument(0)));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            CalendarEventEntity event = call.getArgument(0);
            storedIds.add(event.getId());
            return event;
        });

        var seeder = new DevelopmentCalendarDataSeeder(repository);
        var args = new DefaultApplicationArguments(new String[0]);
        seeder.run(args);
        seeder.run(args);

        org.assertj.core.api.Assertions.assertThat(storedIds).hasSize(3);
        ArgumentCaptor<CalendarEventEntity> events = ArgumentCaptor.forClass(CalendarEventEntity.class);
        verify(repository, times(3)).saveAndFlush(events.capture());
        org.assertj.core.api.Assertions.assertThat(events.getAllValues())
                .extracting(CalendarEventEntity::getStatus)
                .containsExactlyInAnyOrder(CalendarEventStatus.BUSY, CalendarEventStatus.BUSY,
                        CalendarEventStatus.CANCELLED);
    }
}
