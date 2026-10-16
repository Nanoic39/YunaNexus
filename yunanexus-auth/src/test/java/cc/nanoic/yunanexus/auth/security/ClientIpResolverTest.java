package cc.nanoic.yunanexus.auth.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
    @Test
    void rejectsForgedHeadersFromUntrustedPeer() {
        var r = new MockHttpServletRequest();
        r.setRemoteAddr("198.51.100.5");
        r.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("198.51.100.5", ClientIpResolver.resolve(r, "172.29.10.0/24"));
    }

    @Test
    void acceptsOnlySingleLiteralFromTrustedProxy() {
        var r = new MockHttpServletRequest();
        r.setRemoteAddr("172.29.10.7");
        r.addHeader("X-Real-IP", "203.0.113.9");
        assertEquals("203.0.113.9", ClientIpResolver.resolve(r, "172.29.10.0/24"));
        r.removeHeader("X-Real-IP");
        r.addHeader("X-Real-IP", "203.0.113.9, 127.0.0.1");
        assertEquals("172.29.10.7", ClientIpResolver.resolve(r, "172.29.10.0/24"));
    }

    @Test
    void authUtcTimestampsAreExplicit() throws Exception {
        var view = new cc.nanoic.yunanexus.auth.domain.UserView();
        view.setLastLoginTime(java.time.LocalDateTime.of(2026, 10, 16, 16, 0));
        String json = new tools.jackson.databind.ObjectMapper().writeValueAsString(view);
        assertTrue(json.contains("2026-10-16T16:00:00Z"), json);
    }
}
