package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class TokenRequest {
    private String grantType;
    private String code;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String codeVerifier;
    /** 刷新令牌（grant_type=refresh_token 时必填） */
    private String refreshToken;
}