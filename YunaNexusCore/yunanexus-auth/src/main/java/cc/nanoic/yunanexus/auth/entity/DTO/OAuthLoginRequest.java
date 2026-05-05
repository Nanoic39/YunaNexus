package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class OAuthLoginRequest {
    /**
     * 授权模式：password / client_credentials
     */
    private String grantType;

    private String clientUuid;
    private String clientSecret;

    // password 模式
    private String username;
    private String password;
}
