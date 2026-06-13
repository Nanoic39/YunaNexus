package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class AuthorizeRequest {
    private String responseType;
    private String clientId;
    private String redirectUri;
    private String scope;
    private String state;
    private String codeChallenge;
    private String codeChallengeMethod;
}