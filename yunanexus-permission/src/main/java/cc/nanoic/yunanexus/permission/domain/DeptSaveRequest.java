package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增 / 更新请求。<p>
 *
 * 部门层级（{@code parentId}）创建后不可变更；如需调整层级请新建并迁移绑定。
 *
 * @author yunanexus
 */
@Data
public class DeptSaveRequest {

    /** 部门编码（唯一） */
    @NotBlank(message = "deptCode 不能为空")
    @Size(max = 64, message = "deptCode 长度不能超过 64")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "deptCode 仅支持字母、数字、下划线、点、中划线")
    private String deptCode;

    /** 部门名称 */
    @NotBlank(message = "deptName 不能为空")
    @Size(max = 128, message = "deptName 长度不能超过 128")
    private String deptName;

    /** 父部门 ID（0=根；更新时忽略） */
    private Long parentId;

    /** 排序（升序，为空默认 0） */
    private Integer sort;

    /** 状态：1-启用 0-禁用（为空时新增默认启用） */
    private Integer status;
}
