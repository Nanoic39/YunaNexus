package cc.nanoic.yunanexus.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-部门绑定（单部门）。
 *
 * @author yunanexus
 */
@Data
@TableName("perm_user_dept")
public class PermUserDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局用户 ID */
    private Long userId;

    /** 部门 ID */
    private Long deptId;

    private LocalDateTime createTime;
}
