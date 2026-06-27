package cc.nanoic.yunanexus.auth.entity.VO;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OAuthClientVO {
    private Long id;
    private String uuid;
    private String clientName;
    private Integer clientType;
    private String grantTypes;
    private String scope;
    private String redirectUri;
    private String description;
    private Integer auditStatus;
    private String auditOpinion;
    private String applicantGlobalId;
    private String auditorGlobalId;
    private String auditedAt;
    private Integer status;
    private String createdAt;
    private String updatedAt;
}