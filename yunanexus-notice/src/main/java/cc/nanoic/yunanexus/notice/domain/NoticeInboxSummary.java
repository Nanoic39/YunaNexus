package cc.nanoic.yunanexus.notice.domain;

import lombok.Data;

/** 当前用户已送达站内信的全局计数。@author yunanexus */
@Data
public class NoticeInboxSummary {
    private long total;
    private long unread;
}
