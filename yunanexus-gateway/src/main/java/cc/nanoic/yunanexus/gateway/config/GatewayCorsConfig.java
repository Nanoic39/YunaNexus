package cc.nanoic.yunanexus.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 配置（roadmap 4.8）：精确来源白名单，禁止通配符 {@code *}。<p>
 *
 * 白名单为空表示不启用跨域（不注册 CORS 过滤器）。
 *
 * @author yunanexus
 */
@Configuration
public class GatewayCorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(GatewayProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.getCorsAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            for (String origin : origins) {
                if (origin == null || origin.isBlank() || "*".equals(origin.trim())) {
                    throw new IllegalStateException(
                            "CORS 白名单禁止使用通配符 *（yunanexus.gateway.cors-allowed-origins）");
                }
            }
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN",
                    "X-Trace-Id", "X-Admin-Token"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration("/**", config);
        }
        return new CorsWebFilter(source);
    }
}
