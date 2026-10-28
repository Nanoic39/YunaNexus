package cc.nanoic.yunanexus.notice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知偏好。
 *
 * <p>{@code category = "*"} 表示对该通道的全部通知生效；具体模板代码优先级更高。
 *
 * @author yunanexus
 */
@Data
@TableName("notice_preference")
public class NoticePreference {

    /** 通配类别 */
    public static final String ANY_CATEGORY = "*";

    private Long userId;
    private Integer channel;
    private String category;
    private Boolean enabled;
    /** 免打扰开始（当天第几分钟） */
    private Integer quietStart;
    /** 免打扰结束（当天第几分钟） */
    private Integer quietEnd;
    private LocalDateTime updatedAt;
}
