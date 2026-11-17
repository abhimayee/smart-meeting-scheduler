package com.example.scheduler.user.application;

import com.example.scheduler.shared.domain.EmailAddress;
import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.user.api.UserDtos;
import com.example.scheduler.user.infrastructure.persistence.UserEntity;
import com.example.scheduler.user.infrastructure.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserDtos.UserResponse create(UserDtos.CreateUserRequest request) {
        var profile = userMapper.toDomain(request);
        var email = profile.email().value();
        if (userRepository.existsByEmail(email)) throw new DataIntegrityViolationException("A user with this email already exists");
        var entity = new UserEntity(java.util.UUID.randomUUID(), email, profile.name(), profile.timeZone().getId());
        entity.replaceWorkingHours(userMapper.toEntities(profile.workingHours()));
        try {
            return userMapper.toResponse(userRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new DataIntegrityViolationException("A user with this email already exists", exception);
        }
    }

    @Transactional(readOnly = true)
    public UserDtos.UserResponse get(String email) {
        var normalizedEmail = new EmailAddress(email).value();
        return userRepository.findByEmail(normalizedEmail).map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
    }

    @Transactional
    public UserDtos.UserResponse updateWorkingHours(String email, UserDtos.UpdateWorkingHoursRequest request) {
        var normalizedEmail = new EmailAddress(email).value();
        var hours = userMapper.toDomain(request.workingHours());
        var entity = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        entity.replaceWorkingHours(userMapper.toEntities(hours));
        return userMapper.toResponse(entity);
    }
}
