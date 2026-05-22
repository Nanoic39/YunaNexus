package cc.nanoic.yunanexus.gateway.support;

import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.gateway.config.YunaGatewayProperties;
import jakarta.annotation.Resource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@Component
public class AuthTokenValidationClient {
    private static final ParameterizedTypeReference<Result<Map<String, Object>>> RESULT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    @Resource
    private WebClient.Builder loadBalancedWebClientBuilder;

    @Resource
    private YunaGatewayProperties properties;

    public Mono<Map<String, Object>> parseAuthorization(String authorization) {
        return loadBalancedWebClientBuilder.build()
                .post()
                .uri(properties.getSecurity().getAuthParseUri())
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .bodyToMono(RESULT_TYPE)
                .timeout(properties.getSecurity().getAuthTimeout())
                .map(result -> {
                    if (result == null || result.getCode() != R.SUCCESS.getCode() || result.getData() == null) {
                        return Collections.<String, Object>emptyMap();
                    }
                    return result.getData();
                })
                .onErrorReturn(Collections.emptyMap());
    }
}
