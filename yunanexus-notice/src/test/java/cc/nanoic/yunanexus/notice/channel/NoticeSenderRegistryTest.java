package cc.nanoic.yunanexus.notice.channel;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.NoticeChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link NoticeSenderRegistry} 单元测试。
 *
 * @author yunanexus
 */
class NoticeSenderRegistryTest {

    @Test
    void resolvesSenderByChannel() {
        NoticeSender inner = senderOf(NoticeChannel.INNER);
        NoticeSender email = senderOf(NoticeChannel.EMAIL);
        NoticeSenderRegistry registry = new NoticeSenderRegistry(List.of(inner, email));

        assertSame(inner, registry.get(NoticeChannel.INNER));
        assertSame(email, registry.get(NoticeChannel.EMAIL));
    }

    @Test
    void unsupportedChannelThrows() {
        NoticeSenderRegistry registry = new NoticeSenderRegistry(List.of(senderOf(NoticeChannel.INNER)));

        BizException ex = assertThrows(BizException.class, () -> registry.get(NoticeChannel.SMS));
        assertEquals(ErrorCode.NOTICE_CHANNEL_UNSUPPORTED.getCode(), ex.getCode());
    }

    @Test
    void nullChannelThrows() {
        NoticeSenderRegistry registry = new NoticeSenderRegistry(List.of(senderOf(NoticeChannel.INNER)));

        BizException ex = assertThrows(BizException.class, () -> registry.get(null));
        assertEquals(ErrorCode.NOTICE_CHANNEL_UNSUPPORTED.getCode(), ex.getCode());
    }

    private NoticeSender senderOf(NoticeChannel channel) {
        NoticeSender sender = mock(NoticeSender.class);
        when(sender.channel()).thenReturn(channel);
        return sender;
    }
}
