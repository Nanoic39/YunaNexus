package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class Users {
    @TableId
    private byte[] globalId; // 用户主键全局ID(用于系统内各服务透传操作用户, 全局ID)
    private String uuid; // 用户UUID(外显, 用于系统内外传递用户信息)
    private Integer routeVersion; // 路由版本(用于路由选择, 默认0)
    private LocalDateTime createdAt; // 账号创建时间戳
}
