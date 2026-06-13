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
}