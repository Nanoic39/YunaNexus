package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import cc.nanoic.yunanexus.gateway.support.GatewayErrorWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionCheckFilter} 单元测试。<p>
 *
 * 覆盖：未启用 / 未配置地址 / 免鉴权路径 / 缺少身份头 的直通分支，以及动作映射。
 *
 * @author yunanexus
 */
class PermissionCheckFilterTest {

    private GatewayProperties properties;
    private GatewayFilterChain chain;
    private PermissionCheckFilter filter;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        GatewayErrorWriter errorWriter = mock(GatewayErrorWriter.class);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        filter = new PermissionCheckFilter(properties, errorWriter);
    }

    private void enable() {
        properties.getPermission().setEnabled(true);
        properties.getPermission().setCheckUrl("http://localhost:8300/internal/permission/check");
    }

    @Test
    void 未启用时直接放行() {
        properties.getPermission().setEnabled(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/file/x").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void 启用但未配置地址时直接放行() {
        properties.getPermission().setEnabled(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/file/x").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void 免鉴权路径直接放行() {
        enable();
        properties.getPermitPaths().add("/api/v1/auth/login");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void 缺少身份头时直接放行() {
        enable();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/file/x").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void 动作映射() {
        assertEquals("read", PermissionCheckFilter.resolveAction(HttpMethod.GET));
        assertEquals("read", PermissionCheckFilter.resolveAction(HttpMethod.HEAD));
        assertEquals("write", PermissionCheckFilter.resolveAction(HttpMethod.POST));
        assertEquals("write", PermissionCheckFilter.resolveAction(HttpMethod.PUT));
        assertEquals("write", PermissionCheckFilter.resolveAction(HttpMethod.PATCH));
        assertEquals("delete", PermissionCheckFilter.resolveAction(HttpMethod.DELETE));
        assertEquals("read", PermissionCheckFilter.resolveAction(null));
    }
}
