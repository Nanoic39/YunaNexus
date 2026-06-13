package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_economy")
public class UserEconomy {
    @TableId
    private byte[] globalId;
    private Long exp; // 经验值
    private Long coin; // 货币
    private LocalDateTime updatedAt; // 更新时间戳
}
