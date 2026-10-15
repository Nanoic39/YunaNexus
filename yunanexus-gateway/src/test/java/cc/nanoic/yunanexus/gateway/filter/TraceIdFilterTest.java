package cc.nanoic.yunanexus.gateway.filter;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TraceIdFilter} 单元测试。
 *
 * @author yunanexus
 */
class TraceIdFilterTest {

    private TraceIdFilter filter;
    private AtomicReference<ServerWebExchange> captured;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter(new GatewayProperties());
        captured = new AtomicReference<>();
    }

    private GatewayFilterChain chain() {
        return exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void propagatesValidTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header("X-Trace-Id", "abc-123_XYZ.9").build());

        filter.filter(exchange, chain()).block();

        assertEquals("abc-123_XYZ.9", captured.get().getRequest().getHeaders().getFirst("X-Trace-Id"));
        assertEquals("abc-123_XYZ.9", exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void generatesTraceIdWhenAbsent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").build());

        filter.filter(exchange, chain()).block();

        String traceId = captured.get().getRequest().getHeaders().getFirst("X-Trace-Id");
        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
        assertEquals(traceId, exchange.getResponse().getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    void regeneratesInvalidTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header("X-Trace-Id", "bad value!").build());

        filter.filter(exchange, chain()).block();

        String traceId = captured.get().getRequest().getHeaders().getFirst("X-Trace-Id");
        assertNotEquals("bad value!", traceId);
        assertTrue(traceId.matches("[A-Za-z0-9._-]+"));
    }

    @Test
    void regeneratesOverlongTraceId() {
        String overlong = "a".repeat(65);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header("X-Trace-Id", overlong).build());

        filter.filter(exchange, chain()).block();

        assertNotEquals(overlong, captured.get().getRequest().getHeaders().getFirst("X-Trace-Id"));
    }
}
