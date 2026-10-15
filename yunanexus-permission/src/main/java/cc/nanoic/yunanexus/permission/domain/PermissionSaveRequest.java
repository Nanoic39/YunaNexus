package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限新增 / 更新请求。
 *
 * @author yunanexus
 */
@Data
public class PermissionSaveRequest {

    /** 权限标识（唯一） */
    @NotBlank(message = "permCode 不能为空")
    @Size(max = 128, message = "permCode 长度不能超过 128")
    private String permCode;

    /** 资源标识（URL 或资源名，支持 Ant 通配） */
    @NotBlank(message = "resource 不能为空")
    @Size(max = 255, message = "resource 长度不能超过 255")
    private String resource;

    /** 动作 */
    @NotBlank(message = "action 不能为空")
    @Size(max = 32, message = "action 长度不能超过 32")
    private String action;

    /** 权限描述 */
    @Size(max = 255, message = "description 长度不能超过 255")
    private String description;

    /** 状态：1-启用 0-禁用（为空时新增默认启用） */
    private Integer status;
}
