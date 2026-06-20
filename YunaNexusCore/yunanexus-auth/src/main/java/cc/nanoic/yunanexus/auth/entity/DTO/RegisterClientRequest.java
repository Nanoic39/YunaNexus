package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class RegisterClientRequest {
    private String clientName;
    private String grantTypes;
    private String scope;
    private String redirectUri;
    private String description;
}