package cc.nanoic.yunanexus.notice.mq;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.config.NoticeProperties;
import cc.nanoic.yunanexus.notice.domain.NoticeMessage;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 通知消息生产者（RocketMQ 原生客户端）。<p>
 *
 * 随应用启动 / 关闭：{@link #start()} 创建并启动 {@link DefaultMQProducer}，
 * {@link #stop()} 关闭。基于固定 18 级延迟等级投递延迟消息（roadmap 7.8）。
 *
 * @author yunanexus
 */
@Component
public class NoticeProducer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NoticeProducer.class);

    private final NoticeProperties properties;
    private final ObjectMapper objectMapper;

    private DefaultMQProducer producer;
    private volatile boolean running;

    public NoticeProducer(NoticeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        NoticeProperties.Rocketmq mq = properties.getRocketmq();
        if (!mq.isEnabled()) {
            log.warn("RocketMQ 未启用（yunanexus.notice.rocketmq.enabled=false），通知消息将不投递");
            return;
        }
        try {
            DefaultMQProducer instance = new DefaultMQProducer(mq.getProducerGroup());
            instance.setNamesrvAddr(mq.getNameServer());
            instance.setSendMsgTimeout(mq.getSendTimeoutMillis());
            instance.start();
            this.producer = instance;
            this.running = true;
            log.info("通知 RocketMQ Producer 启动成功 group={} nameServer={} topic={}",
                    mq.getProducerGroup(), mq.getNameServer(), mq.getTopic());
        } catch (Exception e) {
            this.running = false;
            log.error("通知 RocketMQ Producer 启动失败，通知将无法投递: {}", e.getMessage(), e);
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
     * 投递一条通知消息。
     *
     * @param message    消息体
     * @param delayLevel 延迟等级（<=0 立即投递）
     * @throws BizException 生产者未就绪或投递失败
     */
    public void send(NoticeMessage message, int delayLevel) {
        if (!running || producer == null) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "RocketMQ Producer 未就绪");
        }
        try {
            Message msg = new Message(properties.getRocketmq().getTopic(),
                    properties.getRocketmq().getTag(),
                    objectMapper.writeValueAsBytes(message));
            if (delayLevel > 0) {
                msg.setDelayTimeLevel(delayLevel);
            }
            SendResult result = producer.send(msg);
            log.debug("通知消息已投递 noticeId={} recordId={} msgId={}",
                    message.getNoticeId(), message.getRecordId(), result.getMsgId());
        } catch (Exception e) {
            throw new BizException(ErrorCode.NOTICE_SEND_FAILED, "投递通知消息失败: " + e.getMessage());
        }
    }
}
