package cc.nanoic.yunanexus.auth.entity.DTO;

import lombok.Data;

@Data
public class PermissionEvaluateRequest {
    private String[] permissionCodes;
    private boolean allMatch;
}
