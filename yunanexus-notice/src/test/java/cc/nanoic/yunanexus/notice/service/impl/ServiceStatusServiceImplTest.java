package cc.nanoic.yunanexus.notice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.PortalElement;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import tools.jackson.databind.ObjectMapper;

import java.net.http.*;
import java.time.*;
import java.util.List;

class ServiceStatusServiceImplTest {
    private final DiscoveryClient discovery = mock(DiscoveryClient.class);
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private ServiceStatusServiceImpl service() {
        return new ServiceStatusServiceImpl(
                discovery,
                mapper,
                Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC),
                http);
    }

    private DefaultServiceInstance instance() {
        var instance =
                new DefaultServiceInstance("one", "yunanexus-auth", "localhost", 8100, false);
        instance.getMetadata().put("management.port", "8101");
        return instance;
    }

    @SuppressWarnings("unchecked")
    private void respond(int code, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
    }

    @Test
    void aliasesShareCacheAndManagementPort() throws Exception {
        when(discovery.getInstances("yunanexus-auth")).thenReturn(List.of(instance()));
        respond(200, "{\"status\":\"UP\"}");
        var service = service();
        try {
            var result = service.getStatus("auth");
            assertEquals("svc-auth", result.key());
            assertEquals("up", result.status());
            assertEquals(100, result.availability());
            assertEquals(List.of("up"), result.history());
            assertSame(result, service.getStatus("svc-auth"));
            assertSame(result, service.getStatus("yunanexus-auth"));
            verify(discovery, times(1)).getInstances("yunanexus-auth");
            verify(http)
                    .send(
                            argThat(
                                    (HttpRequest r) ->
                                            r.uri().getPort() == 8101
                                                    && r.uri()
                                                            .getPath()
                                                            .equals("/actuator/health")),
                            any(HttpResponse.BodyHandler.class));
        } finally {
            service.close();
        }
    }

    @Test
    void partialFailureIsDegraded() throws Exception {
        var missing = new DefaultServiceInstance("two", "yunanexus-auth", "localhost", 8100, false);
        when(discovery.getInstances("yunanexus-auth")).thenReturn(List.of(instance(), missing));
        respond(200, "{\"status\":\"UP\"}");
        var service = service();
        try {
            var result = service.getStatus("auth");
            assertEquals("degraded", result.status());
            assertEquals(50, result.availability());
        } finally {
            service.close();
        }
    }

    @Test
    void summaryIncludesOfflineServicesAndEqualAverage() throws Exception {
        when(discovery.getInstances("yunanexus-auth")).thenReturn(List.of(instance()));
        respond(200, "{\"status\":\"UP\"}");
        var service = service();
        try {
            var summary = service.getSummary();
            assertEquals(9, summary.services().size());
            assertEquals("svc-gateway", summary.services().getFirst().key());
            assertEquals("degraded", summary.status());
            assertEquals(11.11, summary.availability());
        } finally {
            service.close();
        }
    }

    @Test
    void failuresBecomeDownAndUnknownNameIsRejected() throws Exception {
        when(discovery.getInstances("yunanexus-meta"))
                .thenThrow(new IllegalStateException("offline"));
        when(discovery.getInstances("yunanexus-auth")).thenReturn(List.of(instance()));
        respond(503, "{\"status\":\"DOWN\"}");
        var service = service();
        try {
            assertEquals("down", service.getStatus("meta").status());
            assertEquals("down", service.getStatus("auth").status());
            assertThrows(BizException.class, () -> service.getStatus("http://localhost"));
        } finally {
            service.close();
        }
    }

    @Test
    void invalidHealthResponseIsDown() throws Exception {
        when(discovery.getInstances("yunanexus-auth")).thenReturn(List.of(instance()));
        respond(200, "not-json");
        var service = service();
        try {
            assertEquals("down", service.getStatus("auth").status());
        } finally {
            service.close();
        }
    }

    @Test
    void expiredCacheRecordsRecoveryAndDailyHistory() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Instant start = Instant.parse("2026-10-15T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        when(clock.millis()).thenReturn(start.toEpochMilli());
        when(discovery.getInstances("yunanexus-auth")).thenReturn(List.of(instance()));
        respond(503, "{}");
        var service = new ServiceStatusServiceImpl(discovery, mapper, clock, http);
        try {
            assertEquals("down", service.getStatus("auth").status());
            when(clock.millis()).thenReturn(start.plusSeconds(30).toEpochMilli());
            respond(200, "{\"status\":\"UP\"}");
            var recovered = service.getStatus("auth");
            assertEquals("up", recovered.status());
            assertEquals(50, recovered.availability());
            assertEquals(List.of("degraded"), recovered.history());
            Instant tomorrow = start.plusSeconds(86400);
            when(clock.instant()).thenReturn(tomorrow);
            when(clock.millis()).thenReturn(tomorrow.toEpochMilli());
            assertEquals(List.of("degraded", "up"), service.getStatus("auth").history());
            Instant gap = tomorrow.plusSeconds(86400 * 3);
            when(clock.instant()).thenReturn(gap);
            when(clock.millis()).thenReturn(gap.toEpochMilli());
            assertEquals(List.of("up"), service.getStatus("auth").history());
        } finally {
            service.close();
        }
    }

    @Test
    void unsupportedHistoryRangeIsRejectedBeforeAnyProbe() {
        var service = service();
        try {
            assertThrows(BizException.class, () -> service.getHistory("auth", Integer.MAX_VALUE));
            assertThrows(BizException.class, () -> service.getSummary(-1));
            verifyNoInteractions(discovery);
        } finally {
            service.close();
        }
    }

    @Test
    void portalStatusConfigurationRoundTrips() {
        String json =
                "{\"type\":\"services\",\"statusUrl\":\"/api/v1/status\","
                        + "\"statusIntervalSeconds\":30,\"items\":[{\"id\":\"svc-auth\","
                        + "\"statusUrl\":\"/api/v1/status/auth\"}]}";
        var element = mapper.readValue(json, PortalElement.class);
        var copy = mapper.readValue(mapper.writeValueAsString(element), PortalElement.class);
        assertEquals("/api/v1/status", copy.getStatusUrl());
        assertEquals(30, copy.getStatusIntervalSeconds());
        assertEquals("/api/v1/status/auth", copy.getItems().getFirst().getStatusUrl());
    }
}
