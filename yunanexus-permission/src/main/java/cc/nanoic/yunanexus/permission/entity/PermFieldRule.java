package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段级权限规则（L5：字段 / 列级读写控制，Phase C）。<p>
 *
 * 以「主体（用户 / 角色 / 部门）→ 资源类型 → 字段 → 访问类型」描述字段授权，
 * 供业务序列化层决定字段是否可读 / 可写（含掩码）。命中即受限：deny 优先于 allow。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_field_rule")
public class PermFieldRule {

    /** 主体类型：用户 */
    public static final int SUBJECT_USER = 1;
    /** 主体类型：角色 */
    public static final int SUBJECT_ROLE = 2;
    /** 主体类型：部门 */
    public static final int SUBJECT_DEPT = 3;

    /** 访问类型：读 */
    public static final int ACCESS_READ = 1;
    /** 访问类型：写 */
    public static final int ACCESS_WRITE = 2;

    /** 效果：允许 */
    public static final int EFFECT_ALLOW = 1;
    /** 效果：拒绝 */
    public static final int EFFECT_DENY = 0;

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主体类型：1-用户 2-角色 3-部门 */
    private Integer subjectType;

    /** 主体 ID（用户 ID / 角色 ID / 部门 ID） */
    private Long subjectId;

    /** 资源类型（如 user_profile） */
    private String resourceType;

    /** 字段名（如 phone / email） */
    private String fieldName;

    /** 访问类型：1-读 2-写 */
    private Integer access;

    /** 效果：1-允许 0-拒绝 */
    private Integer effect;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
