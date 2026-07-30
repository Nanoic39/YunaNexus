package cc.nanoic.yunanexus.auth.oauth2.exception;

/**
 * 请求参数缺失或无效 — 对应 RFC 6749 {@code invalid_request}.
 * <p>HTTP 400.</p>
 */
public class InvalidRequestException extends OAuth2Exception {

    public InvalidRequestException(String description) {
        super("invalid_request", description, 400, null, null);
    }

    public InvalidRequestException(String description, String redirectUri, String state) {
        super("invalid_request", description, 302, redirectUri, state);
    }
}
