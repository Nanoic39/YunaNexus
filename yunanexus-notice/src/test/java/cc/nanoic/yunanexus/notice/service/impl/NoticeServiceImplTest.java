package cc.nanoic.yunanexus.notice.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.channel.NoticeSender;
import cc.nanoic.yunanexus.notice.channel.NoticeSenderRegistry;
import cc.nanoic.yunanexus.notice.client.MetaIdClient;
import cc.nanoic.yunanexus.notice.config.NoticeProperties;
import cc.nanoic.yunanexus.notice.domain.NoticeChannel;
import cc.nanoic.yunanexus.notice.domain.NoticeMessage;
import cc.nanoic.yunanexus.notice.domain.NoticeRecordView;
import cc.nanoic.yunanexus.notice.domain.NoticeSendRequest;
import cc.nanoic.yunanexus.notice.entity.NoticeRecord;
import cc.nanoic.yunanexus.notice.entity.NoticeTemplate;
import cc.nanoic.yunanexus.notice.mapper.NoticeRecordMapper;
import cc.nanoic.yunanexus.notice.mapper.NoticeTemplateMapper;
import cc.nanoic.yunanexus.notice.mq.NoticeProducer;
import cc.nanoic.yunanexus.notice.render.TemplateRenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link NoticeServiceImpl} 单元测试（发送入队 / 投递回写 / 站内信）。
 *
 * @author yunanexus
 */
class NoticeServiceImplTest {

    private static final String TEMPLATE_CODE = "ORDER_PAID";
    private static final String RECEIVER = "1001";

    private NoticeTemplateMapper templateMapper;
    private NoticeRecordMapper recordMapper;
    private MetaIdClient metaIdClient;
    private NoticeProducer noticeProducer;
    private NoticeSenderRegistry senderRegistry;
    private cc.nanoic.yunanexus.notice.service.NoticePreferenceService preferenceService;
    private cc.nanoic.yunanexus.notice.service.NoticeDedupService dedupService;
    private NoticeServiceImpl service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(NoticeTemplateMapper.class);
        recordMapper = mock(NoticeRecordMapper.class);
        metaIdClient = mock(MetaIdClient.class);
        noticeProducer = mock(NoticeProducer.class);
        senderRegistry = mock(NoticeSenderRegistry.class);
        NoticeProperties properties = new NoticeProperties();
        preferenceService = mock(cc.nanoic.yunanexus.notice.service.NoticePreferenceService.class);
        dedupService = mock(cc.nanoic.yunanexus.notice.service.NoticeDedupService.class);
        when(dedupService.shouldDeliver(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
        service =
                new NoticeServiceImpl(
                        templateMapper,
                        recordMapper,
                        metaIdClient,
                        new TemplateRenderer(),
                        noticeProducer,
                        senderRegistry,
                        properties,
                        new ObjectMapper(), preferenceService, dedupService);
    }

    private NoticeTemplate template(int channel, int status) {
        NoticeTemplate template = new NoticeTemplate();
        template.setTemplateCode(TEMPLATE_CODE);
        template.setTemplateName("订单支付");
        template.setChannel(channel);
        template.setStatus(status);
        template.setTitle("订单 ${order}");
        template.setContent("金额 ${amount}");
        return template;
    }

    private NoticeRecord record(long id, long noticeId, int channel, String receiver, int status) {
        NoticeRecord record = new NoticeRecord();
        record.setId(id);
        record.setNoticeId(noticeId);
        record.setTemplateCode(TEMPLATE_CODE);
        record.setChannel(channel);
        record.setReceiver(receiver);
        record.setStatus(status);
        record.setRetryCount(0);
        return record;
    }

    private NoticeSendRequest sendRequest(List<String> receivers) {
        NoticeSendRequest request = new NoticeSendRequest();
        request.setTemplateCode(TEMPLATE_CODE);
        request.setReceivers(receivers);
        request.setParams(Map.of("order", "A123", "amount", "99.00"));
        return request;
    }

    // ---------- send ----------

    @Test
    void sendRendersPersistsAndEnqueuesPerReceiver() {
        when(templateMapper.selectByCode(TEMPLATE_CODE))
                .thenReturn(template(NoticeChannel.INNER.getCode(), NoticeTemplate.STATUS_ENABLED));
        when(metaIdClient.nextId("notice"))
                .thenReturn(Result.success(9001L), Result.success(9002L));

        List<NoticeRecord> persisted = new ArrayList<>();
        when(recordMapper.insert(any(NoticeRecord.class)))
                .thenAnswer(
                        invocation -> {
                            NoticeRecord inserted = invocation.getArgument(0);
                            inserted.setId((long) (persisted.size() + 1));
                            persisted.add(inserted);
                            return 1;
                        });
        when(recordMapper.selectByNoticeId(anyLong()))
                .thenAnswer(
                        invocation -> {
                            long noticeId = invocation.getArgument(0);
                            return persisted.stream()
                                    .filter(r -> r.getNoticeId() == noticeId)
                                    .findFirst()
                                    .orElse(null);
                        });

        List<NoticeRecordView> views = service.send(sendRequest(List.of(RECEIVER, "1002")));

        assertEquals(2, views.size());
        assertEquals("订单 A123", views.get(0).getTitle());
        assertEquals("金额 99.00", views.get(0).getContent());
        assertEquals(NoticeRecord.STATUS_PENDING, views.get(0).getStatus());
        assertEquals("站内信", views.get(0).getChannelLabel());
        assertEquals(9001L, persisted.get(0).getNoticeId());
        verify(noticeProducer, times(2)).send(any(NoticeMessage.class), eq(3));
    }

    @Test
    void sendRejectsUnknownTemplate() {
        when(templateMapper.selectByCode(TEMPLATE_CODE)).thenReturn(null);

        BizException ex =
                assertThrows(
                        BizException.class, () -> service.send(sendRequest(List.of(RECEIVER))));
        assertEquals(ErrorCode.NOTICE_TEMPLATE_NOT_FOUND.getCode(), ex.getCode());
        verify(recordMapper, never()).insert(any(NoticeRecord.class));
    }

    @Test
    void sendRejectsDisabledTemplate() {
        when(templateMapper.selectByCode(TEMPLATE_CODE))
                .thenReturn(
                        template(NoticeChannel.INNER.getCode(), NoticeTemplate.STATUS_DISABLED));

        BizException ex =
                assertThrows(
                        BizException.class, () -> service.send(sendRequest(List.of(RECEIVER))));
        assertEquals(ErrorCode.NOTICE_TEMPLATE_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void sendRejectsUnsupportedChannel() {
        when(templateMapper.selectByCode(TEMPLATE_CODE))
                .thenReturn(template(NoticeChannel.INNER.getCode(), NoticeTemplate.STATUS_ENABLED));
        NoticeSendRequest request = sendRequest(List.of(RECEIVER));
        request.setChannel(9);

        BizException ex = assertThrows(BizException.class, () -> service.send(request));
        assertEquals(ErrorCode.NOTICE_CHANNEL_UNSUPPORTED.getCode(), ex.getCode());
    }

    @Test
    void sendFailsWhenIdAllocationFails() {
        when(templateMapper.selectByCode(TEMPLATE_CODE))
                .thenReturn(template(NoticeChannel.INNER.getCode(), NoticeTemplate.STATUS_ENABLED));
        when(metaIdClient.nextId("notice")).thenReturn(Result.error(500, "meta down"));

        BizException ex =
                assertThrows(
                        BizException.class, () -> service.send(sendRequest(List.of(RECEIVER))));
        assertEquals(ErrorCode.NOTICE_SEND_FAILED.getCode(), ex.getCode());
        verify(recordMapper, never()).insert(any(NoticeRecord.class));
    }

    // ---------- dispatch ----------

    @Test
    void dispatchMarksSentOnSuccess() {
        NoticeRecord record =
                record(
                        5L,
                        9001L,
                        NoticeChannel.INNER.getCode(),
                        RECEIVER,
                        NoticeRecord.STATUS_PENDING);
        when(recordMapper.selectById(5L)).thenReturn(record);
        NoticeSender sender = mock(NoticeSender.class);
        when(senderRegistry.get(NoticeChannel.INNER)).thenReturn(sender);

        service.dispatch(5L);

        assertEquals(NoticeRecord.STATUS_SENT, record.getStatus());
        assertNotNull(record.getSendTime());
        assertNull(record.getErrorMsg());
        verify(sender).send(record);
        verify(recordMapper).updateById(record);
    }

    @Test
    void dispatchMarksFailedAndTruncatesErrorOnException() {
        NoticeRecord record =
                record(
                        5L,
                        9001L,
                        NoticeChannel.EMAIL.getCode(),
                        "a@b.com",
                        NoticeRecord.STATUS_PENDING);
        when(recordMapper.selectById(5L)).thenReturn(record);
        NoticeSender sender = mock(NoticeSender.class);
        when(senderRegistry.get(NoticeChannel.EMAIL)).thenReturn(sender);
        String longMessage = "smtp".repeat(150);
        org.mockito.Mockito.doThrow(new RuntimeException(longMessage)).when(sender).send(record);

        service.dispatch(5L);

        // 状态机：首次失败不直接判死，而是安排退避重试（maxAttempts 默认 5）
        assertEquals(NoticeRecord.STATUS_PENDING, record.getStatus());
        assertEquals(1, record.getRetryCount());
        org.junit.jupiter.api.Assertions.assertNotNull(record.getNextRetryTime(), "应安排下次重试时间");
        org.junit.jupiter.api.Assertions.assertFalse(Boolean.TRUE.equals(record.getDeadLetter()),
                "首次失败不应进入死信");
        assertEquals(500, record.getErrorMsg().length(), "失败原因应截断至列宽上限");
        verify(recordMapper).updateById(record);
    }

    @Test
    void dispatchIgnoresMissingRecord() {
        when(recordMapper.selectById(5L)).thenReturn(null);

        service.dispatch(5L);

        verify(recordMapper, never()).updateById(any(NoticeRecord.class));
        verify(noticeProducer, never()).send(any(NoticeMessage.class), anyInt());
    }

    // ---------- inbox / markRead / getRecord ----------

    @Test
    void inboxMapsRecordsToViews() {
        when(recordMapper.countInbox(RECEIVER)).thenReturn(1L);
        when(recordMapper.selectInboxPage(eq(RECEIVER), anyInt(), anyInt()))
                .thenReturn(
                        List.of(
                                record(
                                        1L,
                                        9001L,
                                        NoticeChannel.INNER.getCode(),
                                        RECEIVER,
                                        NoticeRecord.STATUS_SENT)));

        PageResult<NoticeRecordView> result = service.inbox(RECEIVER, 1, 20);

        assertEquals(1, result.getRecords().size());
        assertEquals(9001L, result.getRecords().get(0).getNoticeId());
        assertEquals("站内信", result.getRecords().get(0).getChannelLabel());
    }

    @Test
    void markReadSucceedsWhenRowAffected() {
        when(recordMapper.markRead(9001L, RECEIVER)).thenReturn(1);

        service.markRead(9001L, RECEIVER);

        verify(recordMapper).markRead(9001L, RECEIVER);
    }

    @Test
    void markReadThrowsWhenNoRowAffected() {
        when(recordMapper.markRead(9001L, RECEIVER)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.markRead(9001L, RECEIVER));
        assertEquals(ErrorCode.NOTICE_RECORD_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getRecordThrowsWhenMissing() {
        when(recordMapper.selectByNoticeId(9001L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getRecord(9001L));
        assertEquals(ErrorCode.NOTICE_RECORD_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void filtersApplyBeforePaginationAndInvalidRangesNeverQueryDatabase() {
        when(recordMapper.countInboxFiltered(RECEIVER, 1)).thenReturn(25L);
        when(recordMapper.selectInboxFiltered(RECEIVER, 1, 10, 10)).thenReturn(List.of());
        assertEquals(25, service.inbox(RECEIVER, 2, 10, 1).getTotal());
        verify(recordMapper).selectInboxFiltered(RECEIVER, 1, 10, 10);
        assertThrows(BizException.class, () -> service.inbox(RECEIVER, 1, 10, 2));
        assertThrows(BizException.class, () -> service.inbox(RECEIVER, Integer.MAX_VALUE, 100));
    }

    @Test
    void alreadyReadIsIdempotentOnlyForTheSameRecipientAndInnerChannel() {
        var value = record(1L, 9001L, NoticeChannel.INNER.getCode(), RECEIVER, 3);
        when(recordMapper.selectByNoticeId(9001L)).thenReturn(value);
        assertDoesNotThrow(() -> service.markRead(9001L, RECEIVER));
        assertThrows(BizException.class, () -> service.markRead(9001L, "another-user"));
        value.setChannel(2);
        assertThrows(BizException.class, () -> service.markRead(9001L, RECEIVER));
    }

    @Test
    void allReadAndGlobalSummaryAreAlwaysScopedToReceiver() {
        when(recordMapper.markAllRead(RECEIVER)).thenReturn(31);
        assertEquals(31, service.markAllRead(RECEIVER));
        verify(recordMapper).markAllRead(RECEIVER);
        var summary = new cc.nanoic.yunanexus.notice.domain.NoticeInboxSummary();
        summary.setTotal(45);
        summary.setUnread(31);
        when(recordMapper.inboxSummary(RECEIVER)).thenReturn(summary);
        assertSame(summary, service.inboxSummary(RECEIVER));
    }
}
