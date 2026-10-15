package cc.nanoic.yunanexus.notice.channel;

import cc.nanoic.yunanexus.notice.domain.NoticeChannel;
import cc.nanoic.yunanexus.notice.entity.NoticeRecord;

/**
 * 通知通道发送器。<p>
 *
 * 每个通道实现一个（站内信 / 邮件 / 短信），由 {@link NoticeSenderRegistry} 按通道装配；
 * {@link #send(NoticeRecord)} 抛异常即视为投递失败。
 *
 * @author yunanexus
 */
public interface NoticeSender {

    /**
     * 本发送器负责的通道。
     */
    NoticeChannel channel();

    /**
     * 投递一条通知记录。
     *
     * @param record 已渲染的通知记录
     * @throws RuntimeException 投递失败
     */
    void send(NoticeRecord record);
}
