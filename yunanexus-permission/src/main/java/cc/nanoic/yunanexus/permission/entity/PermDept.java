package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门（组织架构）。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_dept")
public class PermDept {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;
    /** 根部门的父 ID */
    public static final long ROOT_PARENT_ID = 0L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 部门编码（唯一） */
    private String deptCode;

    /** 部门名称 */
    private String deptName;

    /** 父部门 ID（0=根） */
    private Long parentId;

    /** 排序（升序） */
    private Integer sort;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime updateTime;
}
