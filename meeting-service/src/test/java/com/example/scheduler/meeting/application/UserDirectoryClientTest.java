package com.example.scheduler.meeting.application;

import com.example.scheduler.shared.exception.ResourceNotFoundException;
import com.example.scheduler.shared.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.time.ZoneId;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

class UserDirectoryClientTest {
    @Test void readsOrganizerIanaTimeZoneForSearchWindow() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/users/a%40example.com"))
                .andRespond(withSuccess("""
                        {"email":"a@example.com","displayName":"Organizer","timeZone":"America/New_York","workingHours":[]}
                        """, MediaType.APPLICATION_JSON));
        var client = new UserDirectoryClient(builder, "http://users");

        assertEquals(ZoneId.of("America/New_York"), client.timeZoneFor("a@example.com"));
        server.verify();
    }

    @Test void mapsMissingUserToNotFound() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/users/missing%40example.com"))
                .andRespond(withResourceNotFound());
        var client = new UserDirectoryClient(builder, "http://users");
        assertThrows(ResourceNotFoundException.class, () -> client.requireExisting("missing@example.com"));
        server.verify();
    }

    @Test void mapsUnavailableUserServiceToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/users/a%40example.com"))
                .andRespond(withServerError());
        var client = new UserDirectoryClient(builder, "http://users");
        assertThrows(ServiceUnavailableException.class, () -> client.requireExisting("a@example.com"));
        server.verify();
    }

    @Test void mapsUserServiceReadTimeoutToServiceUnavailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("http://users/api/users/a%40example.com"))
                .andRespond(withException(new IOException("Read timed out")));
        var client = new UserDirectoryClient(builder, "http://users");

        assertThrows(ServiceUnavailableException.class, () -> client.requireExisting("a@example.com"));
        server.verify();
    }
}
