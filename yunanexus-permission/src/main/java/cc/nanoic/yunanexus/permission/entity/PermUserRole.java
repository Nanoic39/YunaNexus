package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色绑定。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_user_role")
public class PermUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局用户 ID */
    private Long userId;

    /** 角色 ID */
    private Long roleId;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;
}
