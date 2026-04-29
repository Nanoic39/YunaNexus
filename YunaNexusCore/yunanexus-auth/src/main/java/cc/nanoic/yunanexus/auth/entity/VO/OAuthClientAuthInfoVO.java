package cc.nanoic.yunanexus.auth.entity.VO;

import lombok.Data;

@Data
public class OAuthClientAuthInfoVO {
    private String uuid;
    private String clientName;
    private String clientSecret;
    private Integer clientType;
    private Integer auditStatus;
    private String authorizedGrantTypes;
    private String scope;
    private String scopeLimit;
    private String resourceIds;
    private String redirectUri;
    private String redirectWhitelist;
    private Integer accessTokenValidity;
    private Integer refreshTokenValidity;
    private Integer autoApprove;
    private Integer status;
}