package cc.nanoic.yunanexus.notice.controller;
import cc.nanoic.yunanexus.notice.domain.NoticeRecordView;
import cc.nanoic.yunanexus.notice.service.NoticeService;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NoticeInboxOwnershipTest {
    @Test
    void onlyRecipientCanReadDeliveredInboxMessage() {
        var service=mock(NoticeService.class);
        var controller=new NoticeController();
        ReflectionTestUtils.setField(controller,"noticeService",service);
        var record=new NoticeRecordView();record.setReceiver("42");record.setChannel(1);record.setStatus(1);
        when(service.getRecord(99L)).thenReturn(record);
        assertSame(record,controller.inboxRecord(99L,42L).getData());
        assertThrows(BizException.class,()->controller.inboxRecord(99L,43L));
        assertThrows(BizException.class,()->controller.inboxRecord(99L,null));
        record.setChannel(2);
        assertThrows(BizException.class,()->controller.inboxRecord(99L,42L));
        record.setChannel(1);record.setStatus(0);
        assertThrows(BizException.class,()->controller.inboxRecord(99L,42L));
        record.setStatus(3);
        assertSame(record,controller.inboxRecord(99L,42L).getData());
    }
}
