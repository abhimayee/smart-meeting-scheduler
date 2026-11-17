package com.example.scheduler.shared.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class OutboundHttpClientConfigurationTest {
    @Test
    void appliesConfiguredConnectAndReadTimeoutsToRestClient() {
        var builder = RestClient.builder();
        new OutboundHttpClientConfiguration()
                .outboundHttpTimeoutCustomizer(Duration.ofMillis(1200), Duration.ofMillis(3400))
                .customize(builder);

        var factory = assertInstanceOf(SimpleClientHttpRequestFactory.class,
                ReflectionTestUtils.getField(builder, "requestFactory"));
        assertEquals(1200, ReflectionTestUtils.getField(factory, "connectTimeout"));
        assertEquals(3400, ReflectionTestUtils.getField(factory, "readTimeout"));
    }
}
