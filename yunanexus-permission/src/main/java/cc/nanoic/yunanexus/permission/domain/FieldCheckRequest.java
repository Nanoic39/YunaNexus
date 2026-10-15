package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字段级校验请求（内部接口，Phase C）。
 *
 * @author yunanexus
 */
@Data
public class FieldCheckRequest {

    /** 全局用户 ID */
    @NotNull(message = "userId 不能为空")
    @PositiveOrZero(message = "userId 不能为负数")
    private Long userId;

    /** 资源类型（如 user_profile） */
    @NotBlank(message = "resourceType 不能为空")
    @Size(max = 64, message = "resourceType 长度不能超过 64")
    private String resourceType;

    /** 访问类型：1-读 2-写 */
    @NotNull(message = "access 不能为空")
    @Min(value = 1, message = "access 取值为 1~2")
    @Max(value = 2, message = "access 取值为 1~2")
    private Integer access;
}
