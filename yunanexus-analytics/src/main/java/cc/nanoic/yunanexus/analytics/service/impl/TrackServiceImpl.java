package cc.nanoic.yunanexus.analytics.service.impl;

import cc.nanoic.yunanexus.analytics.config.AnalyticsProperties;
import cc.nanoic.yunanexus.analytics.domain.TrackAccepted;
import cc.nanoic.yunanexus.analytics.domain.TrackEvent;
import cc.nanoic.yunanexus.analytics.mq.AnalyticsProducer;
import cc.nanoic.yunanexus.analytics.service.TrackService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 埋点事件接入服务实现（roadmap 10.1 / 10.2）。<p>
 *
 * 处理流程：批次与限流校验 → 逐条补全（{@code eventId} / {@code ingestTime} / {@code ip}）
 * → 投递 MQ。遵循「失败隔离」：单条投递失败仅计入 {@code rejected}，不影响整批与客户端主链路。
 *
 * @author yunanexus
 */
@Service
public class TrackServiceImpl implements TrackService {

    private static final Logger log = LoggerFactory.getLogger(TrackServiceImpl.class);

    private final AnalyticsProducer producer;
    private final AnalyticsProperties properties;
    private final ObjectMapper objectMapper;
    private final cc.nanoic.yunanexus.analytics.store.EventStore eventStore;

    /** 固定窗口限流用：当前窗口起始秒与窗口内累计事件数 */
    private final AtomicLong currentWindowSecond = new AtomicLong(-1);
    private final AtomicLong windowCount = new AtomicLong(0);

    public TrackServiceImpl(AnalyticsProducer producer, AnalyticsProperties properties, ObjectMapper objectMapper,
                            cc.nanoic.yunanexus.analytics.store.EventStore eventStore) {
        this.producer = producer;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.eventStore = eventStore;
    }

    @Override
    public TrackAccepted track(List<TrackEvent> events, String clientIp) {
        if (events == null || events.isEmpty()) {
            throw new BizException(ErrorCode.EVENT_INVALID, "事件数组不能为空");
        }
        AnalyticsProperties.Ingest ingest = properties.getIngest();
        if (events.size() > ingest.getMaxBatchSize()) {
            throw new BizException(ErrorCode.EVENT_INVALID,
                    "单次上报事件数超过上限 " + ingest.getMaxBatchSize());
        }
        checkRateLimit(events.size(), ingest.getMaxQps());

        long now = System.currentTimeMillis();
        int accepted = 0;
        int rejected = 0;
        for (TrackEvent event : events) {
            if (event == null || event.getEventType() == null || event.getEventType().isBlank()) {
                rejected++;
                continue;
            }
            if (!valid(event)) {
                rejected++;
                continue;
            }
            normalize(event, clientIp, now);
            // 落库成功才算接入成功：库是分析的唯一事实来源，MQ 失败只影响下游实时消费
            boolean stored = eventStore.append(event, clientIp);
            if (!stored) {
                rejected++;
                continue;
            }
            if (!producer.send(event)) {
                log.debug("埋点已落库但投递 MQ 失败（不影响分析数据）eventId={}", event.getEventId());
            }
            accepted++;
        }
        log.debug("埋点批次接入完成 size={} accepted={} rejected={}", events.size(), accepted, rejected);
        return TrackAccepted.of(accepted, rejected);
    }

    /**
     * 入库前的必要条件校验。
     *
     * <p>eventType 是必填且要写进 VARCHAR(64)：超长会让整条 insert 失败，
     * 事件在"看起来成功"的情况下被丢弃——这是最坏的一种数据丢失。
     * eventTime 允许 30 天回溯以容忍客户端补传，但拒绝明显穿越的时间戳。
     *
     * @param event 事件
     * @return 通过返回 true
     */
    private boolean valid(TrackEvent event) {
        String type = event.getEventType();
        if (type == null || type.isBlank()) {
            return false;
        }
        if (type.length() > 64) {
            log.debug("eventType 超长已拒绝: length={}", type.length());
            return false;
        }
        Long eventTime = event.getEventTime();
        if (eventTime != null && eventTime > 0) {
            long now = System.currentTimeMillis();
            long minAllowed = now - 30L * 24 * 3600 * 1000;
            long maxAllowed = now + 5L * 60 * 1000;
            if (eventTime < minAllowed || eventTime > maxAllowed) {
                log.debug("eventTime 超出允许区间已拒绝 eventType={} eventTime={}", type, eventTime);
                return false;
            }
        }
        return true;
    }

    /**
     * 固定窗口限流：同一秒内累计事件数超过 {@code maxQps} 时拒绝整批。
     *
     * @param batchSize 本批事件数
     * @param maxQps    每秒事件数上限，{@code <=0} 表示不限流
     */
    private void checkRateLimit(int batchSize, int maxQps) {
        if (maxQps <= 0) {
            return;
        }
        long second = System.currentTimeMillis() / 1000;
        if (currentWindowSecond.get() != second) {
            // 进入新窗口：重置计数（并发下可能重复重置，仅影响精度，不影响正确性）
            currentWindowSecond.set(second);
            windowCount.set(0);
        }
        if (windowCount.addAndGet(batchSize) > maxQps) {
            throw new BizException(ErrorCode.TRACK_RATE_LIMITED,
                    "埋点上报超过限流阈值 " + maxQps + "/s");
        }
    }

    /**
     * 补全事件字段：{@code eventId}（UUID）、{@code ingestTime}、{@code ip}，
     * 并对超过 {@code maxPropsLength} 的 {@code props} 做截断保护（超长则整体丢弃并记日志）。
     */
    private void normalize(TrackEvent event, String clientIp, long now) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (event.getEventTime() == null || event.getEventTime() <= 0) {
            event.setEventTime(now);
        }
        event.setIngestTime(now);
        if ((event.getIp() == null || event.getIp().isBlank()) && clientIp != null) {
            event.setIp(clientIp);
        }
        truncateProps(event);
    }

    /**
     * props 超长保护：序列化长度超过 {@code maxPropsLength}（{@code <=0} 表示不限制）时丢弃。
     */
    private void truncateProps(TrackEvent event) {
        int maxLength = properties.getIngest().getMaxPropsLength();
        if (maxLength <= 0 || event.getProps() == null || event.getProps().isEmpty()) {
            return;
        }
        try {
            if (objectMapper.writeValueAsString(event.getProps()).length() > maxLength) {
                log.debug("props 超过上限 {}，已丢弃 eventType={}", maxLength, event.getEventType());
                event.setProps(null);
            }
        } catch (Exception e) {
            log.debug("props 序列化失败，已丢弃 eventType={}: {}", event.getEventType(), e.getMessage());
            event.setProps(null);
        }
    }
}
