package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_roles")
public class UserRoles {
    @TableId(type = IdType.AUTO)
    private Long id;
    private byte[] globalId; // 用户gid
    private Long roleId; // 角色id
    private Integer status; // 关联状态(0: 取消，1: 启用，2: 删除)
    private LocalDateTime createdAt;
}
