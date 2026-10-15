package cc.nanoic.yunanexus.analytics.mq;

import cc.nanoic.yunanexus.analytics.config.AnalyticsProperties;
import cc.nanoic.yunanexus.analytics.domain.TrackEvent;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 埋点事件生产者（RocketMQ 原生客户端）。<p>
 *
 * 随应用启动 / 关闭 {@link DefaultMQProducer}；上报接口调用 {@link #send(TrackEvent)}
 * 逐条投递到 topic {@code analytics-event}。遵循「失败隔离」：MQ 未启用或投递失败时
 * 仅记录日志并返回 {@code false}，不抛异常、不阻塞埋点主链路。
 *
 * @author yunanexus
 */
@Component
public class AnalyticsProducer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsProducer.class);

    private final AnalyticsProperties properties;
    private final ObjectMapper objectMapper;

    private DefaultMQProducer producer;
    private volatile boolean running;

    public AnalyticsProducer(AnalyticsProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        AnalyticsProperties.Rocketmq mq = properties.getRocketmq();
        if (!mq.isEnabled()) {
            log.warn("RocketMQ 未启用（yunanexus.analytics.rocketmq.enabled=false），埋点事件将不投递");
            return;
        }
        try {
            DefaultMQProducer instance = new DefaultMQProducer(mq.getProducerGroup());
            instance.setNamesrvAddr(mq.getNameServer());
            instance.setSendMsgTimeout((int) mq.getSendTimeoutMillis());
            instance.start();
            this.producer = instance;
            this.running = true;
            log.info("埋点 RocketMQ Producer 启动成功 group={} nameServer={} topic={}",
                    mq.getProducerGroup(), mq.getNameServer(), mq.getTopic());
        } catch (Exception e) {
            this.running = false;
            log.error("埋点 RocketMQ Producer 启动失败，埋点事件将无法投递: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        if (producer != null) {
            producer.shutdown();
            producer = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 最晚启动、最早关闭
        return Integer.MAX_VALUE;
    }

    /**
     * 投递一条埋点事件（失败隔离：失败不抛出）。
     *
     * @param event 埋点事件
     * @return 是否成功投递；{@code false} 表示已降级丢弃
     */
    public boolean send(TrackEvent event) {
        if (!running || producer == null) {
            log.debug("埋点 MQ 未就绪，事件降级丢弃 eventType={}", event.getEventType());
            return false;
        }
        try {
            AnalyticsProperties.Rocketmq mq = properties.getRocketmq();
            Message msg = new Message(mq.getTopic(), mq.getTag(), objectMapper.writeValueAsBytes(event));
            SendResult result = producer.send(msg);
            log.debug("埋点事件已投递 eventType={} msgId={}", event.getEventType(), result.getMsgId());
            return true;
        } catch (Exception e) {
            log.warn("埋点事件投递失败，已降级丢弃 eventType={}: {}", event.getEventType(), e.getMessage());
            return false;
        }
    }
}
