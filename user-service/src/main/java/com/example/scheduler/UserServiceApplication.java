package com.example.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.example.scheduler.user", "com.example.scheduler.shared"})
@AutoConfigurationPackage(basePackages = "com.example.scheduler.user")
@EntityScan(basePackages = "com.example.scheduler.user.infrastructure.persistence")
public class UserServiceApplication {
    public static void main(String[] args) { SpringApplication.run(UserServiceApplication.class, args); }
}
