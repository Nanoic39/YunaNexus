package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 角色新增 / 更新请求。
 *
 * @author yunanexus
 */
@Data
public class RoleSaveRequest {

    /** 角色标识（唯一） */
    @NotBlank(message = "roleCode 不能为空")
    @Size(max = 64, message = "roleCode 长度不能超过 64")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "roleCode 仅支持字母、数字、下划线、点、中划线")
    private String roleCode;

    /** 角色名称 */
    @NotBlank(message = "roleName 不能为空")
    @Size(max = 128, message = "roleName 长度不能超过 128")
    private String roleName;

    /** 角色描述 */
    @Size(max = 255, message = "description 长度不能超过 255")
    private String description;

    /** 数据范围：1-全部 2-本部门 3-本部门及以下 4-仅本人 5-自定义（为空时新增默认 4-仅本人） */
    @Min(value = 1, message = "dataScope 取值为 1~5")
    @Max(value = 5, message = "dataScope 取值为 1~5")
    private Integer dataScope;

    /** 状态：1-启用 0-禁用（为空时新增默认启用） */
    @Min(value = 0, message = "status 取值为 0 或 1")
    @Max(value = 1, message = "status 取值为 0 或 1")
    private Integer status;
}
