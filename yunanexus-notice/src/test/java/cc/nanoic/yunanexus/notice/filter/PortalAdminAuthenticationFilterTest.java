package cc.nanoic.yunanexus.notice.filter;

import cc.nanoic.yunanexus.notice.config.NoticeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class PortalAdminAuthenticationFilterTest {
    @Test
    void missingOrWrongAdminKeyFailsClosed() throws Exception {
        var properties = new NoticeProperties();
        var filter = new PortalAdminAuthenticationFilter(properties, new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/api/v1/notice/portal/admin/pages");
        request.setServletPath("/api/v1/notice/portal/admin/pages");
        request.addHeader("X-User-Id", "1001");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> fail("Ordinary user must not manage portal"));
        assertEquals(401, response.getStatus());
        properties.getAdmin().setToken("test-only-key");
        request = new MockHttpServletRequest("GET", "/api/v1/notice/portal/admin/pages");
        request.setServletPath("/api/v1/notice/portal/admin/pages");
        request.addHeader("X-Admin-Token", "wrong-key");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> fail("Wrong key must fail"));
        assertEquals(401, response.getStatus());
    }

    @Test
    void validAdminKeyPassesAndPublicContentIsUnaffected() throws Exception {
        var properties = new NoticeProperties();
        properties.getAdmin().setToken("test-only-key");
        var filter = new PortalAdminAuthenticationFilter(properties, new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/api/v1/notice/portal/admin/pages");
        request.setServletPath("/api/v1/notice/portal/admin/pages");
        request.addHeader("X-Admin-Token", "test-only-key");
        var passed = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> passed.set(true));
        assertTrue(passed.get());
        request = new MockHttpServletRequest("GET", "/api/v1/notice/portal/content");
        request.setServletPath("/api/v1/notice/portal/content");
        passed.set(false);
        filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> passed.set(true));
        assertTrue(passed.get());
    }
}
