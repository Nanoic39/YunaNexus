package cc.nanoic.yunanexus.auth.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 密钥仅存摘要；实体不得直接作为外部响应。 */
@Data
public class PersonalApiKey {
    private String id;
    private Long userId;
    private String name;
    private String tokenPrefix;
    private String tokenHash;
    private String scopes;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private Integer revoked;
}
