package com.example.scheduler.user.api;

import com.example.scheduler.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDtos.UserResponse create(@Valid @RequestBody UserDtos.CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping("/{email}")
    public UserDtos.UserResponse get(@PathVariable String email) { return userService.get(email); }

    @PutMapping("/{email}/working-hours")
    public UserDtos.UserResponse workingHours(@PathVariable String email,
                                               @Valid @RequestBody UserDtos.UpdateWorkingHoursRequest request) {
        return userService.updateWorkingHours(email, request);
    }
}
