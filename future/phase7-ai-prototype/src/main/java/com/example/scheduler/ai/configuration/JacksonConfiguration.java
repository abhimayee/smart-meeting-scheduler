package com.example.scheduler.ai.configuration;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers Java time serializers used by the AI service API DTOs. */
@Configuration
public class JacksonConfiguration {
    @Bean
    Module javaTimeModule() {
        return new JavaTimeModule();
    }
}
