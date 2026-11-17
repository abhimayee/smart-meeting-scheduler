package com.example.scheduler.ai.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** Test-only bootstrap that keeps the database-free AI service isolated from test-scope service dependencies. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class})
@ComponentScan(basePackages = {"com.example.scheduler.ai", "com.example.scheduler.shared"},
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.example\\.scheduler\\..*Test.*"))
class AiIntegrationTestApplication { }
