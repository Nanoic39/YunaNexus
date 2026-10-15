package cc.nanoic.yunanexus.audit.mq;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import cc.nanoic.yunanexus.audit.domain.AuditMessage;
import cc.nanoic.yunanexus.audit.service.AuditService;
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
 * 审计消息消费者（RocketMQ 原生客户端）。<p>
 *
 * 订阅审计主题，消费到消息后回调 {@link AuditService#persist(AuditMessage)} 按
 * {@code user_id} 路由分片落库。消息解析失败仅记日志并 ACK，避免毒消息无限重投。
 *
 * @author yunanexus
 */
@Component
public class AuditConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final AuditProperties properties;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    private DefaultMQPushConsumer consumer;
    private volatile boolean running;

    public AuditConsumer(AuditProperties properties, ObjectMapper objectMapper, AuditService auditService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    public void start() {
        AuditProperties.Rocketmq mq = properties.getRocketmq();
        if (!mq.isEnabled()) {
            log.warn("RocketMQ 未启用（yunanexus.audit.rocketmq.enabled=false），不启动审计消费者");
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
                        AuditMessage message = objectMapper.readValue(ext.getBody(), AuditMessage.class);
                        auditService.persist(message);
                    } catch (Exception e) {
                        log.error("消费审计消息失败 msgId={}", ext.getMsgId(), e);
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            instance.start();
            this.consumer = instance;
            this.running = true;
            log.info("审计 RocketMQ Consumer 启动成功 group={} topic={} tag={}",
                    mq.getConsumerGroup(), mq.getTopic(), mq.getTag());
        } catch (Exception e) {
            this.running = false;
            log.error("审计 RocketMQ Consumer 启动失败，审计日志将无法落库: {}", e.getMessage(), e);
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
