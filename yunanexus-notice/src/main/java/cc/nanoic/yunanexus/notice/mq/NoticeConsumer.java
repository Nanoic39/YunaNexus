package cc.nanoic.yunanexus.notice.mq;

import cc.nanoic.yunanexus.notice.config.NoticeProperties;
import cc.nanoic.yunanexus.notice.domain.NoticeMessage;
import cc.nanoic.yunanexus.notice.service.NoticeService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 通知消息消费者（RocketMQ 原生客户端）。<p>
 *
 * 订阅通知主题，消费到消息后回调 {@link NoticeService#dispatch(long)} 执行实际投递。
 * 消息解析失败仅记日志并 ACK，避免毒消息无限重投；投递成败由派发器在记录上落状态。
 *
 * @author yunanexus
 */
@Component
public class NoticeConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NoticeConsumer.class);

    private final NoticeProperties properties;
    private final ObjectMapper objectMapper;
    private final NoticeService noticeService;

    private DefaultMQPushConsumer consumer;
    private volatile boolean running;

    public NoticeConsumer(NoticeProperties properties, ObjectMapper objectMapper, NoticeService noticeService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.noticeService = noticeService;
    }

    @Override
    public void start() {
        NoticeProperties.Rocketmq mq = properties.getRocketmq();
        if (!mq.isEnabled()) {
            log.warn("RocketMQ 未启用（yunanexus.notice.rocketmq.enabled=false），不启动通知消费者");
            return;
        }
        try {
            DefaultMQPushConsumer instance = new DefaultMQPushConsumer(mq.getConsumerGroup());
            instance.setNamesrvAddr(mq.getNameServer());
            instance.subscribe(mq.getTopic(), mq.getTag());
            instance.setConsumeThreadMin(2);
            instance.setConsumeThreadMax(8);
            instance.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                for (MessageExt ext : messages) {
                    try {
                        NoticeMessage message = objectMapper.readValue(ext.getBody(), NoticeMessage.class);
                        noticeService.dispatch(message.getRecordId());
                    } catch (Exception e) {
                        log.error("消费通知消息失败 msgId={}", ext.getMsgId(), e);
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            instance.start();
            this.consumer = instance;
            this.running = true;
            log.info("通知 RocketMQ Consumer 启动成功 group={} topic={} tag={}",
                    mq.getConsumerGroup(), mq.getTopic(), mq.getTag());
        } catch (Exception e) {
            this.running = false;
            log.error("通知 RocketMQ Consumer 启动失败，延迟消息将无法消费: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        if (consumer != null) {
            consumer.shutdown();
            consumer = null;
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
}
