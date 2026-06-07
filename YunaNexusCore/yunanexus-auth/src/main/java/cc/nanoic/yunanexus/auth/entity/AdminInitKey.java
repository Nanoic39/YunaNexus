package cc.nanoic.yunanexus.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_init_keys")
public class AdminInitKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String initKey; // 初始化密钥
    private byte[] usedBy; // 被哪个角色使用（Global_Id）
    private LocalDateTime usedAt; // 什么时候被使用
    private LocalDateTime createdAt; // 记录创建时间
}
