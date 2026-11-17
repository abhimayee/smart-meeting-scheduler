package com.example.scheduler.shared.configuration;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class OutboundHttpClientConfiguration {
    @Bean
    RestClientCustomizer outboundHttpTimeoutCustomizer(
            @Value("${outbound.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${outbound.http.read-timeout:5s}") Duration readTimeout) {
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("Outbound HTTP timeouts must be positive");
        }
        return builder -> {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(connectTimeout);
            requestFactory.setReadTimeout(readTimeout);
            builder.requestFactory(requestFactory);
        };
    }
}
