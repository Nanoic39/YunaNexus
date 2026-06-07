package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("roles")
public class Roles {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name; // 名称
    private Integer level; // 权限等级
    private String permissions; // 权限码列表 ["*:*:*:*", "server:page:resources:action"]
    private Integer status; // 角色状态(0: 取消，1: 启用，2: 删除)
    private LocalDateTime createdAt;
}
