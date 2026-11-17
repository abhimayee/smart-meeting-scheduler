package com.example.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.example.scheduler.calendar", "com.example.scheduler.shared"})
@AutoConfigurationPackage(basePackages = "com.example.scheduler.calendar")
@EntityScan(basePackages = "com.example.scheduler.calendar.infrastructure.persistence")
public class CalendarServiceApplication {
    public static void main(String[] args) { SpringApplication.run(CalendarServiceApplication.class, args); }
}
