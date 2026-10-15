package cc.nanoic.yunanexus.gateway.config;

import cc.nanoic.yunanexus.common.core.util.SecureExternalIdGenerator;
import cc.nanoic.yunanexus.gateway.security.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * 网关核心 Bean：外部 ID 解码器、JWT 验签器、时钟。
 *
 * @author yunanexus
 */
@Configuration
public class GatewayBeansConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 从 Secret 读取版本化密钥构造外部 ID 解码器（与认证中心同算法，仅用于解码）。
     */
    @Bean
    public SecureExternalIdGenerator secureExternalIdGenerator(GatewayProperties properties) {
        List<GatewayProperties.KeyVersion> keys = properties.getExternalId().getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("未配置外部 ID 密钥（yunanexus.gateway.external-id.keys）");
        }
        List<SecureExternalIdGenerator.KeyVersion> versions = keys.stream()
                .map(k -> new SecureExternalIdGenerator.KeyVersion(
                        k.getVersion(), k.getAesKeyBase64(), k.getHmacKeyBase64()))
                .toList();
        return new SecureExternalIdGenerator(versions);
    }

    @Bean
    public JwtVerifier jwtVerifier(GatewayProperties properties, Clock clock) {
        return new JwtVerifier(properties, clock);
    }
}
