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

    /** 固定窗口限流用：当前窗口起始秒与窗口内累计事件数 */
    private final AtomicLong currentWindowSecond = new AtomicLong(-1);
    private final AtomicLong windowCount = new AtomicLong(0);

    public TrackServiceImpl(AnalyticsProducer producer, AnalyticsProperties properties, ObjectMapper objectMapper) {
        this.producer = producer;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
            normalize(event, clientIp, now);
            if (producer.send(event)) {
                accepted++;
            } else {
                rejected++;
            }
        }
        log.debug("埋点批次接入完成 size={} accepted={} rejected={}", events.size(), accepted, rejected);
        return TrackAccepted.of(accepted, rejected);
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
