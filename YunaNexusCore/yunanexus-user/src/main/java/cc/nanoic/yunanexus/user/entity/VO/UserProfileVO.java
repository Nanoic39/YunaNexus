package cc.nanoic.yunanexus.user.entity.VO;

import cn.hutool.core.date.DateTime;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfileVO {
    private String nickname; // 用户昵称
    private String avatarUuid; // 头像文件uuid
    private String gender; // 性别
    private DateTime birthday; // 生日
    private String bio; // 简介
    private String showcaseBadges; // 展示徽章ID列表(Json格式)
    private Long exp; // 经验值
    private Long coin; // 货币
    private LocalDateTime updatedAt; // 更新时间戳
}
