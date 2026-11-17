package com.example.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.scheduler.scheduling", "com.example.scheduler.shared"})
public class SchedulingServiceApplication {
    public static void main(String[] args) { SpringApplication.run(SchedulingServiceApplication.class, args); }
}
