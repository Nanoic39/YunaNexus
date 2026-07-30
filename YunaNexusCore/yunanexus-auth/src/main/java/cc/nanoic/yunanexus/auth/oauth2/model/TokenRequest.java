package cc.nanoic.yunanexus.auth.oauth2.model;

import lombok.Data;

/**
 * OAuth2 Token 端点请求体 (RFC 6749 §4.1.3).
 * <p>支持 authorization_code 和 refresh_token 两种 grant_type.</p>
 */
@Data
public class TokenRequest {

    /** 授权类型: authorization_code 或 refresh_token */
    private String grantType;

    /** 授权码（grant_type=authorization_code 时必填） */
    private String code;

    /** 客户端 UUID */
    private String clientId;

    /** 客户端密钥（明文） */
    private String clientSecret;

    /** 回调地址（必须与授权时完全一致） */
    private String redirectUri;

    /** PKCE code_verifier（授权时传了 code_challenge 则必填） */
    private String codeVerifier;

    /** 刷新令牌（grant_type=refresh_token 时必填） */
    private String refreshToken;
}
