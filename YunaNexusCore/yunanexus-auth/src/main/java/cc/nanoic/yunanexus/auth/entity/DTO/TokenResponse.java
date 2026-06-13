package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String tokenType;
    private String scope;
    private String uuid;
}