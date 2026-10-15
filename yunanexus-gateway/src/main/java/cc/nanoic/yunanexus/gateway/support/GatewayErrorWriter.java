package cc.nanoic.yunanexus.gateway.support;

import cc.nanoic.yunanexus.gateway.config.GatewayProperties;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一错误响应写出器。<p>
 *
 * 输出与平台一致的 {@code Result} 结构（code / msg / traceId / timestamp），
 * 不依赖 common-web，避免 Servlet 栈混入。字段为固定集合，采用手工 JSON 构造。
 *
 * @author yunanexus
 */
@Component
public class GatewayErrorWriter {

    private final GatewayProperties properties;

    public GatewayErrorWriter(GatewayProperties properties) {
        this.properties = properties;
    }

    public Mono<Void> write(ServerWebExchange exchange, int httpStatus, int code, String msg) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.valueOf(httpStatus));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        String traceId = response.getHeaders().getFirst(properties.getTraceIdHeader());
        String body = buildBody(code, msg, traceId);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 构造与平台一致的 {@code Result} JSON 字符串（供限流等场景复用）。
     */
    public String buildBody(int code, String msg, String traceId) {
        return "{\"code\":" + code
                + ",\"msg\":\"" + escape(msg) + "\""
                + ",\"tips\":null"
                + ",\"data\":null"
                + ",\"traceId\":" + (traceId == null ? "null" : "\"" + escape(traceId) + "\"")
                + ",\"timestamp\":" + System.currentTimeMillis() + "}";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
