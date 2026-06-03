package cc.nanoic.yunanexus.user.entity;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {
    private byte[] globalId; // 全局ID
    private String nickname; // 用户昵称
    private String avatarUuid; // 头像文件uuid
    private String gender; // 性别
    private DateTime birthday; // 生日
    private String bio; // 简介
    private String showcaseBadges; // 展示徽章ID列表(Json格式)
    private LocalDateTime createdAt; // 创建时间戳
    private LocalDateTime updatedAt; // 更新时间戳
}
