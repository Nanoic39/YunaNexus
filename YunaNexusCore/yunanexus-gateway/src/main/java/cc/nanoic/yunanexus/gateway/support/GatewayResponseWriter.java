package cc.nanoic.yunanexus.gateway.support;

import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import com.alibaba.fastjson2.JSON;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class GatewayResponseWriter {
    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, R code, String tip) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Void> body = Result.fail(code, tip);
        byte[] jsonBytes = toJsonBytes(body);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(jsonBytes)));
    }

    private byte[] toJsonBytes(Object body) {
        try {
            return JSON.toJSONBytes(body);
        } catch (Exception e) {
            return "{\"code\":500,\"msg\":\"服务器内部异常\",\"tip\":\"服务异常，请稍后重试\",\"timestamp\":0,\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
