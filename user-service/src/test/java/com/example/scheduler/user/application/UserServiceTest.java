package com.example.scheduler.user.application;

import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.user.api.UserDtos;
import com.example.scheduler.user.infrastructure.persistence.UserEntity;
import com.example.scheduler.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository repository;
    @Mock UserMapper mapper;
    @InjectMocks UserService service;

    @Test void createPersistsNormalizedProfile() {
        var request = new UserDtos.CreateUserRequest("A@EXAMPLE.COM", "Abhipsa", "Asia/Kolkata", List.of());
        var profile = new com.example.scheduler.user.domain.UserProfile(
                new com.example.scheduler.shared.domain.EmailAddress("A@EXAMPLE.COM"), "Abhipsa",
                java.time.ZoneId.of("Asia/Kolkata"), new com.example.scheduler.user.domain.WorkingHours(List.of()));
        var response = new UserDtos.UserResponse(UUID.randomUUID(), "a@example.com", "Abhipsa", "Asia/Kolkata", List.of(), Instant.now(), Instant.now());
        when(mapper.toDomain(request)).thenReturn(profile);
        when(mapper.toEntities(profile.workingHours())).thenReturn(List.of());
        when(repository.existsByEmail("a@example.com")).thenReturn(false);
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            return saved;
        });
        when(mapper.toResponse(any(UserEntity.class))).thenReturn(response);

        assertEquals("a@example.com", service.create(request).email());
        verify(repository).saveAndFlush(any(UserEntity.class));
    }

    @Test void rejectsDuplicateEmail() {
        var request = new UserDtos.CreateUserRequest("a@example.com", "Abhipsa", "Asia/Kolkata", List.of());
        var profile = new com.example.scheduler.user.domain.UserProfile(
                new com.example.scheduler.shared.domain.EmailAddress("a@example.com"), "Abhipsa",
                java.time.ZoneId.of("Asia/Kolkata"), new com.example.scheduler.user.domain.WorkingHours(List.of()));
        when(mapper.toDomain(request)).thenReturn(profile);
        when(repository.existsByEmail("a@example.com")).thenReturn(true);
        assertThrows(DataIntegrityViolationException.class, () -> service.create(request));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void returnsExistingUserAndNormalizesLookupEmail() {
        var user = new UserEntity(UUID.randomUUID(), "a@example.com", "Abhipsa", "Asia/Kolkata");
        when(repository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        var response = new UserDtos.UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getTimeZone(), List.of(), Instant.now(), Instant.now());
        when(mapper.toResponse(user)).thenReturn(response);
        assertEquals(response, service.get(" A@EXAMPLE.COM "));
    }

    @Test void reportsMissingUser() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get("missing@example.com"));
    }
}
