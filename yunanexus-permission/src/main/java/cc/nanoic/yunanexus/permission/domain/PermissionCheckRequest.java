package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 鉴权判定请求（内部接口，供网关 / 各服务调用）。
 *
 * @author yunanexus
 */
@Data
public class PermissionCheckRequest {

    /** 全局用户 ID */
    @NotNull(message = "userId 不能为空")
    @PositiveOrZero(message = "userId 不能为负数")
    private Long userId;

    /** 资源标识（URL 或资源名） */
    @NotBlank(message = "resource 不能为空")
    private String resource;

    /** 动作 */
    @NotBlank(message = "action 不能为空")
    private String action;

    /** 资源类型（L3 实例级授权，如 file / project；与 resourceId 同时提供时参与判定，可为空） */
    @Size(max = 64, message = "resourceType 长度不能超过 64")
    private String resourceType;

    /** 资源实例 ID（L3 实例级授权；与 resourceType 同时提供时参与判定，可为空） */
    @Size(max = 64, message = "resourceId 长度不能超过 64")
    private String resourceId;

    /** 属性上下文（供 ABAC 策略条件判定，可为空） */
    private Map<String, Object> context;
}
