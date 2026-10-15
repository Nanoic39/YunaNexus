package cc.nanoic.yunanexus.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 行为分析配置（前缀 {@code yunanexus.analytics}）。<p>
 *
 * {@code rocketmq} 控制事件投递；{@code ingest} 约束上报批次与限流；{@code clickhouse}
 * 为离线 / 准实时写入链路参数（消费者使用，见 {@code docs/analytics.md §6}）。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.analytics")
public class AnalyticsProperties {

    private Rocketmq rocketmq = new Rocketmq();

    private Ingest ingest = new Ingest();

    private Clickhouse clickhouse = new Clickhouse();

    /** RocketMQ 事件投递参数 */
    @Data
    public static class Rocketmq {
        /** 是否启用（本地无 MQ 时可关闭，事件仅记录日志、不投递） */
        private boolean enabled = true;
        private String nameServer = "localhost:9876";
        private String producerGroup = "analytics-producer-group";
        private String consumerGroup = "analytics-consumer-group";
        private String topic = "analytics-event";
        private String tag = "track";
        private long sendTimeoutMillis = 3000;
    }

    /** 上报接入参数 */
    @Data
    public static class Ingest {
        /** 单次上报最大事件数，超过返回 {@code 80001} */
        private int maxBatchSize = 200;
        /** 单事件 props 最大长度（字符），超过截断 */
        private int maxPropsLength = 4096;
        /** 全局 QPS 上限（固定窗口限流），超过返回 {@code 80002}；<=0 表示不限流 */
        private int maxQps = 5000;
    }

    /** ClickHouse 写入参数（消费者使用，阶段 10.2 落地） */
    @Data
    public static class Clickhouse {
        /** 是否启用 ClickHouse 写入 */
        private boolean enabled = false;
        private String url = "jdbc:clickhouse://localhost:8123/yunanexus_analytics";
        private String username = "default";
        private String password = "";
        /** 消费者批量写入条数阈值 */
        private int batchSize = 1000;
    }
}
