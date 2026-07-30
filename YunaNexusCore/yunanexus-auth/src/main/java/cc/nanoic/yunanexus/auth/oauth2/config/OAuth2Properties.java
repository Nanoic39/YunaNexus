package cc.nanoic.yunanexus.auth.oauth2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OAuth2 授权服务器专用配置.
 */
@Data
@Component
@ConfigurationProperties(prefix = "yunanexus.oauth2")
public class OAuth2Properties {

    /** 授权码有效期（秒），默认 5 分钟 */
    private long authorizationCodeTtl = 300;

    /** 授权会话有效期（秒），默认 10 分钟 */
    private long authorizationSessionTtl = 600;

    /** 刷新令牌有效期（秒），默认 30 天 */
    private long refreshTokenTtl = 2592000;

    /** 是否强制所有客户端使用 PKCE */
    private boolean pkceRequired = false;

    /** JWT issuer 声明 */
    private String issuer = "https://yunanexus.example.com";
}
