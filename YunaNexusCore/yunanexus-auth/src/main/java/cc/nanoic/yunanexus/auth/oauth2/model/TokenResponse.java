package cc.nanoic.yunanexus.auth.oauth2.model;

import lombok.Data;

/**
 * OAuth2 Token 端点成功响应 (RFC 6749 §5.1).
 */
@Data
public class TokenResponse {

    /** JWT 访问令牌 */
    private String accessToken;

    /** 令牌类型，固定为 "Bearer" */
    private String tokenType;

    /** 访问令牌有效期（秒） */
    private long expiresIn;

    /** 刷新令牌（用于续期） */
    private String refreshToken;

    /** 本次授权范围 */
    private String scope;
}
