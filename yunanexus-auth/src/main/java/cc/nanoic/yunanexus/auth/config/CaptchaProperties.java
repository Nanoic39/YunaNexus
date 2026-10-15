package cc.nanoic.yunanexus.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 验证码配置。<p>
 *
 * 前缀 {@code yunanexus.auth.captcha}。生产环境验证码通过短信/邮件下发，
 * 本地开发可开启 {@code dev-return-code} 回显验证码便于调试。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.auth.captcha")
public class CaptchaProperties {

    /** 验证码有效期（秒） */
    private long ttlSeconds = 300;

    /** 开发环境是否在响应中回显验证码（生产必须为 false） */
    private boolean devReturnCode = false;
}
