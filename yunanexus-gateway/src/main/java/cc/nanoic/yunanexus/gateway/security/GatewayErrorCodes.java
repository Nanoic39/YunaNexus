package cc.nanoic.yunanexus.gateway.security;

/**
 * 网关错误码。<p>
 *
 * 令牌相关码值与认证中心 {@code ErrorCode}（30101~30103）保持一致，便于前端统一处理；
 * 限流码使用 HTTP 语义值，网关不依赖 common-web（避免 Servlet 栈混入）。
 *
 * @author yunanexus
 */
public final class GatewayErrorCodes {

    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int TOKEN_INVALID = 30101;
    public static final int TOKEN_EXPIRED = 30102;
    public static final int TOKEN_REVOKED = 30103;
    public static final int RATE_LIMITED = 429;
    public static final int SERVICE_UNAVAILABLE = 503;

    private GatewayErrorCodes() {
    }
}
