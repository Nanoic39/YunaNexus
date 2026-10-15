package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源实例级授权（L3：按具体对象授权，Phase B）。<p>
 *
 * 以「主体（用户 / 角色 / 部门）→ 资源实例 → 动作」三元组描述授权，
 * 对目标实例具备决定性：命中的 DENY 优先于 ALLOW。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_resource_grant")
public class PermResourceGrant {

    /** 主体类型：用户 */
    public static final int SUBJECT_USER = 1;
    /** 主体类型：角色 */
    public static final int SUBJECT_ROLE = 2;
    /** 主体类型：部门 */
    public static final int SUBJECT_DEPT = 3;

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

    /** 主体类型：1-用户 2-角色 3-部门 */
    private Integer subjectType;

    /** 主体 ID（用户 ID / 角色 ID / 部门 ID） */
    private Long subjectId;

    /** 资源类型（如 file / project） */
    private String resourceType;

    /** 资源实例 ID */
    private String resourceId;

    /** 动作（{@value #ACTION_ANY} 表示任意动作） */
    private String action;

    /** 效果：1-允许 0-拒绝 */
    private Integer effect;

    /** 过期时间（null 表示永久） */
    private LocalDateTime expireTime;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
