package cc.nanoic.yunanexus.common.rocketmq.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RocketMQEventEnvelope<T> {

    /**
     * 事件ID(唯一)
     */
    private String eventId;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 事件发生时间（UTC）
     */
    private Instant occurredAt;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 事件业务数据
     */
    private T payload;

    /**
     * 构建事件封包
     * @param eventType 事件类型
     * @param traceId 链路追踪ID
     * @param payload 事件业务数据
     */
    public static <T> RocketMQEventEnvelope<T> of(String eventType, String traceId, T payload) {
        return RocketMQEventEnvelope.<T>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .occurredAt(Instant.now())
                .traceId(traceId)
                .payload(payload)
                .build();
    }
}
