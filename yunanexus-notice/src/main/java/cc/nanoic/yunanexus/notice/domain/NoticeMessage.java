package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

/**
 * 通知投递消息（RocketMQ 消息体，JSON 序列化）。<p>
 *
 * 仅承载定位信息，消费端据此回查 {@code notice_record} 后实际投递，
 * 避免消息体承载大字段与敏感内容。
 *
 * @author yunanexus
 */
@Data
public class NoticeMessage {

    /** 通知记录主键（{@code notice_record.id}） */
    private Long recordId;

    /** 全局通知 ID */
    private Long noticeId;

    public NoticeMessage() {
    }

    public NoticeMessage(Long recordId, Long noticeId) {
        this.recordId = recordId;
        this.noticeId = noticeId;
    }
}
