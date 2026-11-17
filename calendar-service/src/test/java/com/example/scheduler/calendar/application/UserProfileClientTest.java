package com.example.scheduler.calendar.application;

import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class UserProfileClientTest {
    @Test void mapsMissingUserToNotFound() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/users/missing%40example.com"))
                .andRespond(withResourceNotFound());
        var client = new UserProfileClient(builder, "http://users");
        assertThrows(ResourceNotFoundException.class, () -> client.get("missing@example.com"));
        server.verify();
    }

    @Test void mapsUnavailableUserServiceToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/users/a%40example.com"))
                .andRespond(withServerError());
        var client = new UserProfileClient(builder, "http://users");
        assertThrows(ServiceUnavailableException.class, () -> client.get("a@example.com"));
        server.verify();
    }

    @Test void mapsUserServiceReadTimeoutToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/users/a%40example.com"))
                .andRespond(withException(new IOException("Read timed out")));
        var client = new UserProfileClient(builder, "http://users");

        assertThrows(ServiceUnavailableException.class, () -> client.get("a@example.com"));
        server.verify();
    }
}
