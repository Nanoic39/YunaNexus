package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.auth.config.AdminProperties;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 管理后台密钥鉴权过滤器单元测试。<p>
 *
 * 覆盖路径匹配、密钥未配置、密钥缺失/错误、密钥正确放行并写入 ROLE_ADMIN，
 * 以及自定义请求头名。
 *
 * @author yunanexus
 */
class AdminAuthenticationFilterTest {

    private static final String ADMIN_PATH = AdminAuthenticationFilter.ADMIN_PATH_PREFIX + "/users";

    private AdminProperties properties;
    private ObjectMapper objectMapper;
    private AdminAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        properties = new AdminProperties();
        objectMapper = new ObjectMapper();
        filter = new AdminAuthenticationFilter(properties, new SecurityErrorWriter(objectMapper));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 非管理路径不参与过滤() {
        assertTrue(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/v1/auth/me")));
    }

    @Test
    void 管理路径参与过滤() {
        assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("GET", ADMIN_PATH)));
    }

    @Test
    void 密钥未配置时返回403且业务码为未启用() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("GET", ADMIN_PATH), response, chain);

        assertEquals(403, response.getStatus());
        assertEquals(ErrorCode.ADMIN_DISABLED.getCode(), bodyCode(response));
        verifyNoInteractions(chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void 密钥请求头缺失时返回401() throws Exception {
        properties.setToken("s3cret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(new MockHttpServletRequest("GET", ADMIN_PATH), response, chain);

        assertEquals(401, response.getStatus());
        assertEquals(ErrorCode.ADMIN_TOKEN_INVALID.getCode(), bodyCode(response));
        verifyNoInteractions(chain);
    }

    @Test
    void 密钥不匹配时返回401() throws Exception {
        properties.setToken("s3cret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_PATH);
        request.addHeader("X-Admin-Token", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals(ErrorCode.ADMIN_TOKEN_INVALID.getCode(), bodyCode(response));
        verifyNoInteractions(chain);
    }

    @Test
    void 密钥正确时放行并写入管理员身份() throws Exception {
        properties.setToken("s3cret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_PATH);
        request.addHeader("X-Admin-Token", "s3cret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    void 支持自定义请求头名() throws Exception {
        properties.setToken("s3cret");
        properties.setHeaderName("X-Custom-Admin");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_PATH);
        request.addHeader("X-Custom-Admin", "s3cret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private int bodyCode(MockHttpServletResponse response) throws Exception {
        JsonNode node = objectMapper.readTree(response.getContentAsString());
        return node.get("code").asInt();
    }
}
