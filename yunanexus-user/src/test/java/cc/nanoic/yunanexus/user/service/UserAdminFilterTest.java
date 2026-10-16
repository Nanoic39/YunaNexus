package cc.nanoic.yunanexus.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;
import cc.nanoic.yunanexus.user.security.UserAdminFilter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

import tools.jackson.databind.ObjectMapper;

class UserAdminFilterTest {
    @Test
    void forgedUserHeaderCannotBypassBadgeAdminAuthorization() throws Exception {
        var verifier = mock(AdminBearerVerifier.class);
        when(verifier.verify("Bearer ordinary")).thenReturn(403);
        var filter = new UserAdminFilter(verifier, new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/api/v1/user/admin/badges");
        request.addHeader("Authorization", "Bearer ordinary");
        request.addHeader("X-User-Id", "1");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }
}
