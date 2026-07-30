package cc.nanoic.yunanexus.auth.oauth2.model;

/**
 * RFC 6749 支持的授权类型.
 */
public enum OAuth2GrantType {

    AUTHORIZATION_CODE("authorization_code"),
    REFRESH_TOKEN("refresh_token");

    private final String value;

    OAuth2GrantType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据字符串解析授权类型，不支持则返回 null.
     */
    public static OAuth2GrantType from(String grantType) {
        if (grantType == null) return null;
        for (OAuth2GrantType gt : values()) {
            if (gt.value.equals(grantType)) {
                return gt;
            }
        }
        return null;
    }
}
