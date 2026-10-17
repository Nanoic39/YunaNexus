package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限（资源 + 动作）。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_permission")
public class PermPermission {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限标识（唯一） */
    private String permCode;

    /** 资源标识（URL 或资源名） */
    private String resource;

    /** 动作 */
    private String action;

    /** 权限描述 */
    private String description;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime updateTime;
}
