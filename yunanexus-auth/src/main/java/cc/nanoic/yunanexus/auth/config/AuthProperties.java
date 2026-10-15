package cc.nanoic.yunanexus.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证中心安全配置。<p>
 *
 * 前缀 {@code yunanexus.auth}，集中管理登录失败锁定阈值、JWT 有效期、验证码等
 * 安全相关参数。所有值均有本地开发默认值，生产环境通过环境变量 / Nacos 差异化覆盖
 * （见 docs/deployment.md）。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.auth")
public class AuthProperties {

    /** 登录失败要求验证码的阈值（≥ 此值后登录需验证码） */
    private int captchaThreshold = 3;

    /** 登录失败锁定阈值（≥ 此值后锁定账号） */
    private int lockThreshold = 7;

    /** 锁定时长（分钟） */
    private int lockMinutes = 30;

    /** access token 有效期（秒） */
    private long accessTokenTtlSeconds = 1800;

    /** refresh token 有效期（秒） */
    private long refreshTokenTtlSeconds = 604800;

    /** JWT 签发者 */
    private String issuer = "yunanexus";

    /** JWT 签名密钥轮换周期（天），ADR-015 */
    private int signingKeyRotationDays = 90;

    /** 会话 TTL（秒），与 refresh token 对齐，登出/踢人后立即失效 */
    private long sessionTtlSeconds = 604800;

    /** Redis key 前缀，默认 'auth'，避免多环境冲突 */
    private String redisKeyPrefix = "auth";

    /**
     * 手机号功能开关：控制「手机号作为登录凭证」与「注册携带手机号」。
     * 默认关闭（当前无 SMS 能力，无法校验手机号归属）；待短信通道就绪后置 true 重开，代码零改动。
     */
    private boolean mobileEnabled = false;
}
