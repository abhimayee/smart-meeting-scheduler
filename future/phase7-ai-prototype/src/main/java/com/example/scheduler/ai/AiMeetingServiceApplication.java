package com.example.scheduler.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.scheduler.ai", "com.example.scheduler.shared"})
public class AiMeetingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiMeetingServiceApplication.class, args);
    }
}
