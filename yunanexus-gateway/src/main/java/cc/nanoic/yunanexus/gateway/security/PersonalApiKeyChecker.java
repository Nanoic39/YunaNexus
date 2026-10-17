package cc.nanoic.yunanexus.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/** 密钥每次请求均回源校验，避免撤销后仍因本地缓存继续生效。 */
@Component
public class PersonalApiKeyChecker {
    private final WebClient client = WebClient.builder().build();
    private final String url;

    public PersonalApiKeyChecker(
            @Value(
                            "${yunanexus.gateway.api-key-verification-url:http://localhost:8100/internal/auth/api-key/verify}")
                    String url) {
        this.url = url;
    }

    public record Identity(long userId, int shardIndex) {}

    public Mono<Identity> verify(String token, String method, String path) {
        // 网关与认证中心双重限制精确路由，个人密钥永远不能访问管理接口。
        boolean allowed =
                "GET".equals(method)
                                && ("/api/v1/user/profile".equals(path)
                                        || "/api/v1/file/me".equals(path)
                                        || path.matches("/api/v1/file/me/[1-9][0-9]*/(download|visibility)"))
                        || "POST".equals(method)
                                && ("/api/v1/file/me/upload".equals(path)
                                        || path.matches(
                                                "/api/v1/file/me/[1-9][0-9]*/(delete|rename|visibility)"));
        if (!allowed) return Mono.error(new IllegalArgumentException("Route forbidden"));
        return client.post()
                .uri(url)
                .bodyValue(Map.of("token", token, "method", method, "path", path))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(2))
                .map(
                        result -> {
                            if (!(result.get("code") instanceof Number n)
                                    || n.intValue() != 200
                                    || !(result.get("data") instanceof Map<?, ?> data))
                                throw new IllegalArgumentException("Key rejected");
                            long id = Long.parseLong(String.valueOf(data.get("userId")));
                            int shard = Integer.parseInt(String.valueOf(data.get("shardIndex")));
                            if (id <= 0 || shard < 0 || shard > 15)
                                throw new IllegalArgumentException("Invalid identity");
                            return new Identity(id, shard);
                        });
    }
}
