package cc.nanoic.yunanexus.notice.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.NoticeRecordView;
import cc.nanoic.yunanexus.notice.service.NoticeService;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NoticeInboxOwnershipTest {
    @Test
    void onlyRecipientCanReadDeliveredInboxMessage() {
        var service = mock(NoticeService.class);
        var controller = new NoticeController();
        ReflectionTestUtils.setField(controller, "noticeService", service);
        var record = new NoticeRecordView();
        record.setReceiver("42");
        record.setChannel(1);
        record.setStatus(1);
        when(service.getRecord(99L)).thenReturn(record);
        assertSame(record, controller.inboxRecord(99L, 42L).getData());
        assertThrows(BizException.class, () -> controller.inboxRecord(99L, 43L));
        assertThrows(BizException.class, () -> controller.inboxRecord(99L, null));
        record.setChannel(2);
        assertThrows(BizException.class, () -> controller.inboxRecord(99L, 42L));
        record.setChannel(1);
        record.setStatus(0);
        assertThrows(BizException.class, () -> controller.inboxRecord(99L, 42L));
        record.setStatus(3);
        assertSame(record, controller.inboxRecord(99L, 42L).getData());
    }

    @Test
    void newHttpRoutesBindExplicitParametersAndIgnoreUserSuppliedReceiver() throws Exception {
        var service = mock(NoticeService.class);
        var controller = new NoticeController();
        ReflectionTestUtils.setField(controller, "noticeService", service);
        var mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                controller)
                        .build();
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/notice/inbox")
                                .header("X-User-Id", 42)
                                .param("page", "2")
                                .param("size", "10")
                                .param("status", "1"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());
        verify(service).inbox("42", 2, 10, 1);
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/notice/inbox/summary")
                                .header("X-User-Id", 42))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());
        verify(service).inboxSummary("42");
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/v1/notice/read/all")
                                .header("X-User-Id", 42)
                                .contentType("application/json")
                                .content("{\"receiver\":\"43\"}"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                                .isOk());
        verify(service).markAllRead("42");
        verify(service, never()).markAllRead("43");
        assertThrows(BizException.class, () -> controller.markAllRead(null));
        assertThrows(BizException.class, () -> controller.inboxSummary(0L));
    }
}
