package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class OAuthRefreshRequest {
    private String clientUuid;
    private String clientSecret;
    private String refreshToken;
}