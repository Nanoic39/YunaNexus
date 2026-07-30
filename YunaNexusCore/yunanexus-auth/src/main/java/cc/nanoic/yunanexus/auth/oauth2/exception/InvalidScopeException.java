package cc.nanoic.yunanexus.auth.oauth2.exception;

/**
 * 请求的 scope 超出客户端注册范围 — 对应 RFC 6749 {@code invalid_scope}.
 * <p>HTTP 400 / 302（授权端点）.</p>
 */
public class InvalidScopeException extends OAuth2Exception {

    public InvalidScopeException(String description) {
        super("invalid_scope", description, 400, null, null);
    }

    public InvalidScopeException(String description, String redirectUri, String state) {
        super("invalid_scope", description, 302, redirectUri, state);
    }
}
