package cc.nanoic.yunanexus.notice.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.nanoic.yunanexus.notice.domain.ServiceStatusSummary;
import cc.nanoic.yunanexus.notice.domain.ServiceStatusView;
import cc.nanoic.yunanexus.notice.service.ServiceStatusService;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class ServiceStatusControllerTest {
    @Test
    void httpParametersBindWithoutCompilerParameterMetadata() throws Exception {
        var service = mock(ServiceStatusService.class);
        var controller = new ServiceStatusController();
        ReflectionTestUtils.setField(controller, "serviceStatusService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var value =
                new ServiceStatusView(
                        "svc-auth",
                        "yunanexus-auth",
                        "up",
                        100,
                        List.of(),
                        "2026-10-16T00:00:00Z",
                        List.of());
        when(service.getSummary(24))
                .thenReturn(new ServiceStatusSummary("up", 100, List.of(value)));
        when(service.getSummary(168))
                .thenReturn(new ServiceStatusSummary("up", 100, List.of(value)));
        when(service.getHistory("auth", 720)).thenReturn(value);
        when(service.getHistory("svc-auth", 168)).thenReturn(value);
        mvc.perform(get("/api/v1/status")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/status").param("hours", "168")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/status/auth").param("hours", "720")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/status/svc-auth/history").param("hours", "168"))
                .andExpect(status().isOk());
        verify(service).getSummary(24);
        verify(service).getSummary(168);
        verify(service).getHistory("auth", 720);
        verify(service).getHistory("svc-auth", 168);
    }
}
