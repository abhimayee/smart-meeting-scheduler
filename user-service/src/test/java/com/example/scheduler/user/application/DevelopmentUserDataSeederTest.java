package com.example.scheduler.user.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.scheduler.user.api.UserDtos;
import com.example.scheduler.user.infrastructure.persistence.UserRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DevelopmentUserDataSeederTest {
    @Test
    void repeatedStartupSeedsMissingUsersOnceAndDoesNotReplaceExistingProfiles() {
        UserRepository repository = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        Set<String> storedEmails = new HashSet<>();
        when(repository.existsByEmail(any())).thenAnswer(call -> storedEmails.contains(call.getArgument(0)));
        doAnswer(call -> {
            storedEmails.add(((UserDtos.CreateUserRequest) call.getArgument(0)).email());
            return null;
        }).when(userService).create(any());

        var seeder = new DevelopmentUserDataSeeder(repository, userService);
        var args = new DefaultApplicationArguments(new String[0]);
        seeder.run(args);
        seeder.run(args);

        org.assertj.core.api.Assertions.assertThat(storedEmails).containsExactlyInAnyOrder(
                "scheduler-demo-organizer@example.com", "scheduler-demo-london@example.com",
                "scheduler-demo-new-york@example.com", "scheduler-demo-singapore@example.com");
        verify(userService, times(4)).create(any());
    }
}
