package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class OAuthLoginRequest {
    /**
     * 授权模式：password / client_credentials / refresh_token
     */
    private String grantType;

    private String clientName;
    private String clientSecret;

    // password 模式
    private String username;
    private String password;

    // refresh_token模式
    private String refreshToken;
}
