package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单 / 页面资源（L1：菜单与页面可见性，Phase D）。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_menu")
public class PermMenu {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用 */
    public static final int STATUS_DISABLED = 0;
    /** 根菜单的父 ID */
    public static final long ROOT_PARENT_ID = 0L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 菜单编码（唯一） */
    private String menuCode;

    /** 菜单名称 */
    private String menuName;

    /** 父菜单 ID（0=根） */
    private Long parentId;

    /** 前端路由路径 */
    private String path;

    /** 排序（升序） */
    private Integer sort;

    /** 状态：1-启用 0-禁用 */
    private Integer status;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime updateTime;
}
