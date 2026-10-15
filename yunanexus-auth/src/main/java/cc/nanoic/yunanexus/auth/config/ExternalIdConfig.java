package cc.nanoic.yunanexus.auth.config;

import cc.nanoic.yunanexus.common.core.util.SecureExternalIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部 ID 编解码器配置。<p>
 *
 * 从 {@link ExternalIdProperties} 构建 {@link SecureExternalIdGenerator}，供注册时
 * 编码外部 ID（落库）使用。密钥来自环境变量 / Secret（ADR-011）。
 *
 * @author yunanexus
 */
@Configuration
public class ExternalIdConfig {

    /**
     * 构建外部 ID 编解码器。
     *
     * @param properties 外部 ID 密钥配置
     * @return 编解码器实例
     * @throws IllegalStateException 未配置密钥或密钥非法
     */
    @Bean
    public SecureExternalIdGenerator secureExternalIdGenerator(ExternalIdProperties properties) {
        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            throw new IllegalStateException("未配置外部 ID 密钥（yunanexus.auth.external-id.keys）");
        }
        List<SecureExternalIdGenerator.KeyVersion> versions = new ArrayList<>();
        for (ExternalIdProperties.KeyVersion kv : properties.getKeys()) {
            versions.add(new SecureExternalIdGenerator.KeyVersion(
                    kv.getVersion(), kv.getAesKeyBase64(), kv.getHmacKeyBase64()));
        }
        return new SecureExternalIdGenerator(versions);
    }
}
