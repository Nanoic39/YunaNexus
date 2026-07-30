package cc.nanoic.yunanexus.auth.oauth2.exception;

/**
 * 授权码或 Refresh Token 无效/过期 — 对应 RFC 6749 {@code invalid_grant}.
 * <p>HTTP 400.</p>
 */
public class InvalidGrantException extends OAuth2Exception {

    public InvalidGrantException(String description) {
        super("invalid_grant", description, 400, null, null);
    }
}
