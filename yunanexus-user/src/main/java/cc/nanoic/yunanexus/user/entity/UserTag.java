package cc.nanoic.yunanexus.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户标签实体（{@code user_tag}，每个物理分库各一张）。<p>
 *
 * 由用户自助维护（覆盖式设置），{@code user_id} + {@code tag_code} 用户内唯一，
 * 亦为分库路由键。
 *
 * @author yunanexus
 */
@Data
@TableName("user_tag")
public class UserTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局用户 ID */
    private Long userId;

    /** 逻辑分片编号（0~15） */
    private Integer shardIndex;

    /** 标签编码（用户内唯一） */
    private String tagCode;

    /** 标签名称 */
    private String tagName;

    /** 标签颜色（前端展示） */
    private String color;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
