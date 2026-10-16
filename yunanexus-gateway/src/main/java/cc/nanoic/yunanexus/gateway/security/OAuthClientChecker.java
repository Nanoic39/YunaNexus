package cc.nanoic.yunanexus.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/** 第三方应用每次请求回源检查启用状态和范围；异常拒绝。 */
@Component
public class OAuthClientChecker {
    private final WebClient client = WebClient.builder().build();
    private final String url;

    public OAuthClientChecker(
            @Value(
                            "${yunanexus.gateway.api-key-verification-url:http://localhost:8100/internal/auth/api-key/verify}")
                    String source) {
        url = source.replace("/api-key/verify", "/oauth/allow");
    }

    public Mono<Boolean> allows(String authorization, String method, String path) {
        return client.get()
                .uri(
                        UriComponentsBuilder.fromUriString(url)
                                .queryParam("method", method)
                                .queryParam("path", path)
                                .build()
                                .encode()
                                .toUri())
                .header("Authorization", authorization)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .map(
                        r ->
                                r.get("code") instanceof Number n
                                        && n.intValue() == 200
                                        && Boolean.TRUE.equals(r.get("data")))
                .onErrorReturn(false);
    }
}
