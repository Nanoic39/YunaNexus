package cc.nanoic.yunanexus.notice.channel;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.NoticeChannel;
import cc.nanoic.yunanexus.notice.entity.NoticeRecord;
import org.springframework.stereotype.Component;

/**
 * 短信发送器（占位）。<p>
 *
 * 短信通道暂未接入运营商网关：保留通道枚举与发送器接口，调用即抛
 * {@link ErrorCode#NOTICE_CHANNEL_UNSUPPORTED}，后续接入时替换本实现即可。
 *
 * @author yunanexus
 */
@Component
public class SmsSender implements NoticeSender {

    @Override
    public NoticeChannel channel() {
        return NoticeChannel.SMS;
    }

    @Override
    public void send(NoticeRecord record) {
        throw new BizException(ErrorCode.NOTICE_CHANNEL_UNSUPPORTED, "短信通道暂未接入");
    }
}
