package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-自定义数据范围部门（{@code data_scope=5} 时生效）。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_role_dept")
public class PermRoleDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色 ID */
    private Long roleId;

    /** 部门 ID */
    private Long deptId;

    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;
}
