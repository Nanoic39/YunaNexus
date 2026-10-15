package cc.nanoic.yunanexus.notice.channel;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.NoticeChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 通知通道发送器注册表。<p>
 *
 * 启动时收集全部 {@link NoticeSender} 并按通道归类，发送时按通道取用。
 *
 * @author yunanexus
 */
@Component
public class NoticeSenderRegistry {

    private final Map<NoticeChannel, NoticeSender> senders;

    public NoticeSenderRegistry(List<NoticeSender> senderList) {
        Map<NoticeChannel, NoticeSender> map = new EnumMap<>(NoticeChannel.class);
        for (NoticeSender sender : senderList) {
            map.put(sender.channel(), sender);
        }
        this.senders = Map.copyOf(map);
    }

    /**
     * 取指定通道的发送器。
     *
     * @param channel 通道
     * @return 发送器
     * @throws BizException 通道未接入
     */
    public NoticeSender get(NoticeChannel channel) {
        NoticeSender sender = channel == null ? null : senders.get(channel);
        if (sender == null) {
            throw new BizException(ErrorCode.NOTICE_CHANNEL_UNSUPPORTED,
                    channel == null ? "通道为空" : channel.getLabel());
        }
        return sender;
    }
}
