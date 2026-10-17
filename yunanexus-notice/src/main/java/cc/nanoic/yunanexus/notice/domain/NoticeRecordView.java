package cc.nanoic.yunanexus.notice.domain;

import cc.nanoic.yunanexus.notice.entity.NoticeRecord;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知记录视图。
 *
 * @author yunanexus
 */
@Data
public class NoticeRecordView {

    private Long id;
    private Long noticeId;
    private String templateCode;
    private Integer channel;
    private String channelLabel;
    private String receiver;
    private String title;
    private String content;
    private String params;
    private Integer status;
    private Integer retryCount;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime sendTime;
    private String errorMsg;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;

    public static NoticeRecordView from(NoticeRecord record) {
        NoticeRecordView view = new NoticeRecordView();
        view.setId(record.getId());
        view.setNoticeId(record.getNoticeId());
        view.setTemplateCode(record.getTemplateCode());
        view.setChannel(record.getChannel());
        NoticeChannel channel = NoticeChannel.of(record.getChannel());
        view.setChannelLabel(channel == null ? null : channel.getLabel());
        view.setReceiver(record.getReceiver());
        view.setTitle(record.getTitle());
        view.setContent(record.getContent());
        view.setParams(record.getParams());
        view.setStatus(record.getStatus());
        view.setRetryCount(record.getRetryCount());
        view.setSendTime(record.getSendTime());
        view.setErrorMsg(record.getErrorMsg());
        view.setCreateTime(record.getCreateTime());
        return view;
    }
}
