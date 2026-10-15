package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色权限集合设置请求（覆盖式）。
 *
 * @author yunanexus
 */
@Data
public class PermissionIdsRequest {

    /** 目标权限 ID 集合（覆盖语义：以本次集合替换角色现有绑定） */
    @NotNull(message = "permissionIds 不能为空")
    private List<Long> permissionIds;
}
