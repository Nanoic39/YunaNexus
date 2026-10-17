package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ABAC 策略。<p>
 *
 * {@code conditions} 为 JSON 数组，元素形如
 * {@code {"attribute":"ip","operator":"EQ","value":"10.0.0.1"}}，全部条件以 AND 组合。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_policy")
public class PermPolicy {

    /** 效果：允许 */
    public static final int EFFECT_ALLOW = 1;
    /** 效果：拒绝 */
    public static final int EFFECT_DENY = 0;

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    /** 任意动作通配符 */
    public static final String ACTION_ANY = "*";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略名称 */
    private String policyName;

    /** 资源模式（支持 Ant 通配） */
    private String resource;

    /** 动作（{@value #ACTION_ANY} 表示任意动作） */
    private String action;

    /** 效果：1-允许 0-拒绝 */
    private Integer effect;

    /** 属性条件（JSON 数组） */
    private String conditions;

    /** 优先级（数值越大越先判定） */
    private Integer priority;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime updateTime;
}
