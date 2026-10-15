package cc.nanoic.yunanexus.permission.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ABAC 策略新增 / 更新请求。
 *
 * @author yunanexus
 */
@Data
public class PolicySaveRequest {

    /** 策略名称 */
    @NotBlank(message = "policyName 不能为空")
    @Size(max = 128, message = "policyName 长度不能超过 128")
    private String policyName;

    /** 资源模式（支持 Ant 通配，如 /api/v1/file/**） */
    @NotBlank(message = "resource 不能为空")
    @Size(max = 255, message = "resource 长度不能超过 255")
    private String resource;

    /** 动作（* 表示任意动作） */
    @NotBlank(message = "action 不能为空")
    @Size(max = 32, message = "action 长度不能超过 32")
    private String action;

    /** 效果：1-允许 0-拒绝 */
    @NotNull(message = "effect 不能为空")
    @Min(value = 0, message = "effect 仅支持 0/1")
    @Max(value = 1, message = "effect 仅支持 0/1")
    private Integer effect;

    /** 属性条件（JSON 数组，元素 {attribute,operator,value}） */
    @Size(max = 2048, message = "conditions 长度不能超过 2048")
    private String conditions;

    /** 优先级（数值越大越先判定，为空默认 0） */
    private Integer priority;

    /** 状态：1-启用 0-禁用（为空时新增默认启用） */
    private Integer status;
}
