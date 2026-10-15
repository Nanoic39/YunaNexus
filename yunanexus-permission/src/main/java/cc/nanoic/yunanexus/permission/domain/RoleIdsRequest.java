package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户角色集合设置请求（覆盖式）。
 *
 * @author yunanexus
 */
@Data
public class RoleIdsRequest {

    /** 目标角色 ID 集合（覆盖语义：以本次集合替换用户现有绑定） */
    @NotNull(message = "roleIds 不能为空")
    private List<Long> roleIds;
}
