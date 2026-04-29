package cc.nanoic.yunanexus.auth.entity.VO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthLoginTokenVO {
    private String tokenType; // Token类型
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String scope;
}