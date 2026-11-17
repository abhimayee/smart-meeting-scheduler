package com.example.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.example.scheduler.meeting", "com.example.scheduler.shared"})
@AutoConfigurationPackage(basePackages = "com.example.scheduler.meeting")
@EntityScan(basePackages = "com.example.scheduler.meeting.infrastructure.persistence")
public class MeetingServiceApplication {
    public static void main(String[] args) { SpringApplication.run(MeetingServiceApplication.class, args); }
}
