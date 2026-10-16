package cc.nanoic.yunanexus.notice.service.impl;

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
import cc.nanoic.yunanexus.notice.service.NoticeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通知发送与投递服务实现（roadmap 7.6 ~ 7.8）。
 *
 * <p>发送链路：模板渲染 → 号段取号（{@code notice}）→ 记录落库（待发送）→ 投递延迟消息。<br>
 * 投递链路：MQ 消费者回查记录 → 通道发送器真实投递 → 回写已发送 / 发送失败。
 *
 * @author yunanexus
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeServiceImpl.class);

    /** 号段业务标识（元数据中心 {@code id_segment.biz_tag}） */
    private static final String BIZ_TAG = "notice";

    /** 失败原因落库最大长度（{@code notice_record.error_msg} varchar(512)） */
    private static final int ERROR_MSG_MAX = 500;

    /** 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页大小上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeTemplateMapper templateMapper;
    private final NoticeRecordMapper recordMapper;
    private final MetaIdClient metaIdClient;
    private final TemplateRenderer templateRenderer;
    private final NoticeProducer noticeProducer;
    private final NoticeSenderRegistry senderRegistry;
    private final NoticeProperties properties;
    private final ObjectMapper objectMapper;

    public NoticeServiceImpl(
            NoticeTemplateMapper templateMapper,
            NoticeRecordMapper recordMapper,
            MetaIdClient metaIdClient,
            TemplateRenderer templateRenderer,
            NoticeProducer noticeProducer,
            NoticeSenderRegistry senderRegistry,
            NoticeProperties properties,
            ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.recordMapper = recordMapper;
        this.metaIdClient = metaIdClient;
        this.templateRenderer = templateRenderer;
        this.noticeProducer = noticeProducer;
        this.senderRegistry = senderRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<NoticeRecordView> send(NoticeSendRequest request) {
        NoticeTemplate template = templateMapper.selectByCode(request.getTemplateCode());
        if (template == null) {
            throw new BizException(ErrorCode.NOTICE_TEMPLATE_NOT_FOUND, request.getTemplateCode());
        }
        if (template.getStatus() == null || template.getStatus() != NoticeTemplate.STATUS_ENABLED) {
            throw new BizException(ErrorCode.NOTICE_TEMPLATE_DISABLED, request.getTemplateCode());
        }
        int channelCode =
                request.getChannel() != null ? request.getChannel() : template.getChannel();
        if (NoticeChannel.of(channelCode) == null) {
            throw new BizException(ErrorCode.NOTICE_CHANNEL_UNSUPPORTED, "通道: " + channelCode);
        }

        Map<String, String> params = request.getParams() == null ? Map.of() : request.getParams();
        String title = templateRenderer.render(template.getTitle(), params);
        String content = templateRenderer.render(template.getContent(), params);
        String paramsJson = toJson(params);
        int delayLevel =
                request.getDelayLevel() != null
                        ? request.getDelayLevel()
                        : properties.getRocketmq().getDelayLevel();

        List<NoticeRecordView> views = new ArrayList<>();
        for (String receiver : request.getReceivers()) {
            long noticeId = nextNoticeId();
            NoticeRecord record = new NoticeRecord();
            record.setNoticeId(noticeId);
            record.setTemplateCode(template.getTemplateCode());
            record.setChannel(channelCode);
            record.setReceiver(receiver);
            record.setTitle(title);
            record.setContent(content);
            record.setParams(paramsJson);
            record.setStatus(NoticeRecord.STATUS_PENDING);
            record.setRetryCount(0);
            recordMapper.insert(record);

            noticeProducer.send(new NoticeMessage(record.getId(), noticeId), delayLevel);
            views.add(NoticeRecordView.from(recordMapper.selectByNoticeId(noticeId)));
            log.info(
                    "通知已入队 noticeId={} channel={} receiver={} delayLevel={}",
                    noticeId,
                    channelCode,
                    receiver,
                    delayLevel);
        }
        return views;
    }

    @Override
    public void dispatch(long recordId) {
        NoticeRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            log.warn("通知记录不存在，忽略投递 recordId={}", recordId);
            return;
        }
        try {
            NoticeSender sender = senderRegistry.get(NoticeChannel.of(record.getChannel()));
            sender.send(record);
            record.setStatus(NoticeRecord.STATUS_SENT);
            record.setSendTime(LocalDateTime.now());
            record.setErrorMsg(null);
            recordMapper.updateById(record);
            log.info(
                    "通知投递成功 noticeId={} channel={} receiver={}",
                    record.getNoticeId(),
                    record.getChannel(),
                    record.getReceiver());
        } catch (RuntimeException e) {
            record.setStatus(NoticeRecord.STATUS_FAILED);
            record.setErrorMsg(truncate(e.getMessage()));
            record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
            recordMapper.updateById(record);
            log.error(
                    "通知投递失败 noticeId={} channel={} receiver={}: {}",
                    record.getNoticeId(),
                    record.getChannel(),
                    record.getReceiver(),
                    e.getMessage());
        }
    }

    @Override
    public PageResult<NoticeRecordView> inbox(String receiver, int page, int size) {
        return inbox(receiver, page, size, null);
    }

    @Override
    public PageResult<NoticeRecordView> inbox(String receiver, int page, int size, Integer status) {
        if (status != null && status != 1 && status != 3)
            throw new BizException(ErrorCode.BAD_REQUEST, "站内信状态仅支持 1（未读）和 3（已读）");
        int p = normalizePage(page);
        int s = normalizeSize(size);
        long total =
                status == null
                        ? recordMapper.countInbox(receiver)
                        : recordMapper.countInboxFiltered(receiver, status);
        if (total == 0) return PageResult.empty(p, s);
        var records =
                (status == null
                                ? recordMapper.selectInboxPage(receiver, offset(p, s), s)
                                : recordMapper.selectInboxFiltered(
                                        receiver, status, offset(p, s), s))
                        .stream().map(NoticeRecordView::from).toList();
        return PageResult.of(records, total, p, s);
    }

    @Override
    public cc.nanoic.yunanexus.notice.domain.NoticeInboxSummary inboxSummary(String receiver) {
        return recordMapper.inboxSummary(receiver);
    }

    @Override
    public int markAllRead(String receiver) {
        return recordMapper.markAllRead(receiver);
    }

    @Override
    public void markRead(long noticeId, String receiver) {
        if (recordMapper.markRead(noticeId, receiver) == 0) {
            var record = recordMapper.selectByNoticeId(noticeId);
            if (record != null
                    && receiver.equals(record.getReceiver())
                    && Integer.valueOf(1).equals(record.getChannel())
                    && Integer.valueOf(3).equals(record.getStatus())) return;
            throw new BizException(ErrorCode.NOTICE_RECORD_NOT_FOUND, "通知不存在或不可标记已读: " + noticeId);
        }
    }

    @Override
    public NoticeRecordView getRecord(long noticeId) {
        NoticeRecord record = recordMapper.selectByNoticeId(noticeId);
        if (record == null) {
            throw new BizException(ErrorCode.NOTICE_RECORD_NOT_FOUND, "通知不存在: " + noticeId);
        }
        return NoticeRecordView.from(record);
    }

    @Override
    public PageResult<NoticeRecordView> listRecords(
            String templateCode, String receiver, Integer status, int page, int size) {
        int p = normalizePage(page);
        int s = normalizeSize(size);
        long total = recordMapper.countRecords(templateCode, receiver, status);
        if (total == 0) {
            return PageResult.empty(p, s);
        }
        List<NoticeRecordView> records =
                recordMapper
                        .selectRecordPage(templateCode, receiver, status, offset(p, s), s)
                        .stream()
                        .map(NoticeRecordView::from)
                        .toList();
        return PageResult.of(records, total, p, s);
    }

    /** 领取全局通知 ID（号段模式，ADR-001）。 */
    private long nextNoticeId() {
        Result<Long> result = metaIdClient.nextId(BIZ_TAG);
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "领取全局通知 ID 失败");
        }
        return result.getData();
    }

    /** 序列化模板参数为 JSON 落库（失败视为发送失败）。 */
    private String toJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "序列化模板参数失败: " + e.getMessage());
        }
    }

    private static int normalizePage(int page) {
        if (page > 10000) throw new BizException(ErrorCode.BAD_REQUEST, "页码超出查询上限");
        return Math.max(page, 1);
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static int offset(int page, int size) {
        return (page - 1) * size;
    }

    /** 截断失败原因至列宽上限。 */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MSG_MAX ? message : message.substring(0, ERROR_MSG_MAX);
    }
}
