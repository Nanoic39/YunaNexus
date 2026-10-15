package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户徽章实体（{@code user_badge}，每个物理分库各一张）。<p>
 *
 * 徽章 / 成就通常由系统或运营授予，用户只读；{@code user_id} + {@code badge_code}
 * 用户内唯一，亦为分库路由键。
 *
 * @author yunanexus
 */
@Data
@TableName("user_badge")
public class UserBadge {

    /** 默认徽章等级 */
    public static final int DEFAULT_LEVEL = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局用户 ID */
    private Long userId;

    /** 逻辑分片编号（0~15） */
    private Integer shardIndex;

    /** 徽章编码（用户内唯一） */
    private String badgeCode;

    /** 徽章名称 */
    private String badgeName;

    /** 徽章等级（1~9） */
    private Integer badgeLevel;

    /** 徽章描述 */
    private String description;

    /** 徽章图标 URL */
    private String icon;

    /** 授予时间 */
    private LocalDateTime grantedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
