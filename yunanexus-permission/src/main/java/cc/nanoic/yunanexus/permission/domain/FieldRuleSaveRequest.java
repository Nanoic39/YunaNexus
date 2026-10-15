package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字段级权限规则新增 / 更新请求（Phase C）。
 *
 * @author yunanexus
 */
@Data
public class FieldRuleSaveRequest {

    /** 主体类型：1-用户 2-角色 3-部门 */
    @NotNull(message = "subjectType 不能为空")
    @Min(value = 1, message = "subjectType 取值为 1~3")
    @Max(value = 3, message = "subjectType 取值为 1~3")
    private Integer subjectType;

    /** 主体 ID（用户 ID / 角色 ID / 部门 ID） */
    @NotNull(message = "subjectId 不能为空")
    @Positive(message = "subjectId 必须为正数")
    private Long subjectId;

    /** 资源类型（如 user_profile） */
    @NotBlank(message = "resourceType 不能为空")
    @Size(max = 64, message = "resourceType 长度不能超过 64")
    private String resourceType;

    /** 字段名（如 phone / email） */
    @NotBlank(message = "fieldName 不能为空")
    @Size(max = 64, message = "fieldName 长度不能超过 64")
    private String fieldName;

    /** 访问类型：1-读 2-写 */
    @NotNull(message = "access 不能为空")
    @Min(value = 1, message = "access 取值为 1~2")
    @Max(value = 2, message = "access 取值为 1~2")
    private Integer access;

    /** 效果：1-允许 0-拒绝 */
    @NotNull(message = "effect 不能为空")
    @Min(value = 0, message = "effect 仅支持 0/1")
    @Max(value = 1, message = "effect 仅支持 0/1")
    private Integer effect;

    /** 状态：1-启用 0-禁用（为空时新增默认启用） */
    private Integer status;
}
