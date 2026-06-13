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
    private String applicantGlobalId;
    private Integer status;
    private LocalDateTime createdAt;
}