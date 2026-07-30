package cc.nanoic.yunanexus.auth.oauth2.exception;

/**
 * 不支持的 grant_type — 对应 RFC 6749 {@code unsupported_grant_type}.
 * <p>HTTP 400.</p>
 */
public class UnsupportedGrantTypeException extends OAuth2Exception {

    public UnsupportedGrantTypeException(String description) {
        super("unsupported_grant_type", description, 400, null, null);
    }
}
