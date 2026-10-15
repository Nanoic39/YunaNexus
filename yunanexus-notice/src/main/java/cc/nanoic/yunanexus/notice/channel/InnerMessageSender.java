package cc.nanoic.yunanexus.notice.channel;

import cc.nanoic.yunanexus.notice.domain.NoticeChannel;
import cc.nanoic.yunanexus.notice.entity.NoticeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 站内信发送器。<p>
 *
 * 站内信以 {@code notice_record} 记录本身为载体：落库即「到达」，用户经收件箱接口
 * 查询。投递动作仅记录日志，状态流转由派发器统一置为已发送。
 *
 * @author yunanexus
 */
@Component
public class InnerMessageSender implements NoticeSender {

    private static final Logger log = LoggerFactory.getLogger(InnerMessageSender.class);

    @Override
    public NoticeChannel channel() {
        return NoticeChannel.INNER;
    }

    @Override
    public void send(NoticeRecord record) {
        log.info("站内信已投递 noticeId={} receiver={}", record.getNoticeId(), record.getReceiver());
    }
}
