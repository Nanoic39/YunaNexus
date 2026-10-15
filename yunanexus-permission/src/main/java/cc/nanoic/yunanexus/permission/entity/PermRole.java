package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色（RBAC）。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_role")
public class PermRole {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    /** 数据范围：全部 */
    public static final int DATA_SCOPE_ALL = 1;
    /** 数据范围：本部门 */
    public static final int DATA_SCOPE_DEPT = 2;
    /** 数据范围：本部门及以下 */
    public static final int DATA_SCOPE_DEPT_AND_CHILD = 3;
    /** 数据范围：仅本人 */
    public static final int DATA_SCOPE_SELF = 4;
    /** 数据范围：自定义部门 */
    public static final int DATA_SCOPE_CUSTOM = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色标识（唯一） */
    private String roleCode;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String description;

    /** 数据范围：1-全部 2-本部门 3-本部门及以下 4-仅本人 5-自定义 */
    private Integer dataScope;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
