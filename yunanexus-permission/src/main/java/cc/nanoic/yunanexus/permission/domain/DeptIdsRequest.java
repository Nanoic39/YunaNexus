package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 部门集合设置请求（覆盖式，用于角色自定义数据范围）。
 *
 * @author yunanexus
 */
@Data
public class DeptIdsRequest {

    /** 目标部门 ID 集合（覆盖语义） */
    @NotNull(message = "deptIds 不能为空")
    private List<Long> deptIds;
}
