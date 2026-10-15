package cc.nanoic.yunanexus.audit.mq;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import cc.nanoic.yunanexus.audit.domain.AuditMessage;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 审计消息生产者（RocketMQ 原生客户端）。<p>
 *
 * 随应用启动 / 关闭：{@link #start()} 创建并启动 {@link DefaultMQProducer}，
 * {@link #stop()} 关闭。上报接口调用 {@link #send(AuditMessage)} 异步投递日志。
 *
 * @author yunanexus
 */
@Component
public class AuditProducer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AuditProducer.class);

    private final AuditProperties properties;
    private final ObjectMapper objectMapper;

    private DefaultMQProducer producer;
    private volatile boolean running;

    public AuditProducer(AuditProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        AuditProperties.Rocketmq mq = properties.getRocketmq();
        if (!mq.isEnabled()) {
            log.warn("RocketMQ 未启用（yunanexus.audit.rocketmq.enabled=false），审计日志将不落库");
            return;
        }
        try {
            DefaultMQProducer instance = new DefaultMQProducer(mq.getProducerGroup());
            instance.setNamesrvAddr(mq.getNameServer());
            instance.setSendMsgTimeout((int) mq.getSendTimeoutMillis());
            instance.start();
            this.producer = instance;
            this.running = true;
            log.info("审计 RocketMQ Producer 启动成功 group={} nameServer={} topic={}",
                    mq.getProducerGroup(), mq.getNameServer(), mq.getTopic());
        } catch (Exception e) {
            this.running = false;
            log.error("审计 RocketMQ Producer 启动失败，审计日志将无法入队: {}", e.getMessage(), e);
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
     * 投递一条审计消息。
     *
     * @param message 审计消息
     * @throws BizException 生产者未就绪或投递失败
     */
    public void send(AuditMessage message) {
        if (!running || producer == null) {
            throw new BizException(ErrorCode.AUDIT_MQ_UNAVAILABLE, "RocketMQ Producer 未就绪");
        }
        try {
            Message msg = new Message(properties.getRocketmq().getTopic(),
                    properties.getRocketmq().getTag(),
                    objectMapper.writeValueAsBytes(message));
            SendResult result = producer.send(msg);
            log.debug("审计消息已投递 service={} action={} msgId={}",
                    message.getService(), message.getAction(), result.getMsgId());
        } catch (Exception e) {
            throw new BizException(ErrorCode.AUDIT_REPORT_FAILED, "投递审计消息失败: " + e.getMessage());
        }
    }
}
