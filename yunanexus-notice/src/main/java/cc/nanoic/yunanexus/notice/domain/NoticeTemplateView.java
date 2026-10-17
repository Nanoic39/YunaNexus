package cc.nanoic.yunanexus.notice.domain;

import cc.nanoic.yunanexus.notice.entity.NoticeTemplate;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知模板视图。
 *
 * @author yunanexus
 */
@Data
public class NoticeTemplateView {

    private Long id;
    private String templateCode;
    private String templateName;
    private Integer channel;
    private String channelLabel;
    private String title;
    private String content;
    private Integer status;
    private String remark;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime updateTime;

    public static NoticeTemplateView from(NoticeTemplate template) {
        NoticeTemplateView view = new NoticeTemplateView();
        view.setId(template.getId());
        view.setTemplateCode(template.getTemplateCode());
        view.setTemplateName(template.getTemplateName());
        view.setChannel(template.getChannel());
        NoticeChannel channel = NoticeChannel.of(template.getChannel());
        view.setChannelLabel(channel == null ? null : channel.getLabel());
        view.setTitle(template.getTitle());
        view.setContent(template.getContent());
        view.setStatus(template.getStatus());
        view.setRemark(template.getRemark());
        view.setCreateTime(template.getCreateTime());
        view.setUpdateTime(template.getUpdateTime());
        return view;
    }
}
