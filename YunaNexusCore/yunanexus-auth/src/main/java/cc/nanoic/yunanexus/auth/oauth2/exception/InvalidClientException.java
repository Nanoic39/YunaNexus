package cc.nanoic.yunanexus.auth.oauth2.exception;

/**
 * 客户端认证失败 — 对应 RFC 6749 {@code invalid_client}.
 * <p>HTTP 401，客户端 secret 错误或客户端不存在/已禁用.</p>
 */
public class InvalidClientException extends OAuth2Exception {

    public InvalidClientException(String description) {
        super("invalid_client", description, 401, null, null);
    }

    public InvalidClientException(String description, String redirectUri, String state) {
        super("invalid_client", description, 302, redirectUri, state);
    }
}
