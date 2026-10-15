package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户部门绑定请求。
 *
 * @author yunanexus
 */
@Data
public class UserDeptRequest {

    /** 部门 ID；为 null 表示解绑 */
    @NotNull(message = "deptId 不能为空，解绑请调用 DELETE")
    private Long deptId;
}
