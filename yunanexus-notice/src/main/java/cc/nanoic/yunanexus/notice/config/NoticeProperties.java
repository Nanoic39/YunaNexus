package cc.nanoic.yunanexus.notice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知中心配置项（前缀 {@code yunanexus.notice}）。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.notice")
public class NoticeProperties {

    /** 门户管理接口密钥，生产环境从 Secret 注入。 */
    private Admin admin = new Admin();

    @Data
    public static class Admin {
        private String token = "";
    }

    /** RocketMQ 配置 */
    private Rocketmq rocketmq = new Rocketmq();

    /** 邮件配置 */
    private Mail mail = new Mail();

    private Retry retry = new Retry();

    private DeadLetter deadLetter = new DeadLetter();

    /**
     * 死信告警（邮件）。
     */
    @Data
    public static class DeadLetter {
        private boolean enabled = true;
        /** 告警收件人 */
        private String alertTo = "";
        /** 时间窗口（分钟） */
        private int windowMinutes = 60;
        /** 单封邮件最多列出条数 */
        private int batchSize = 50;
    }

    /**
     * 投递重试策略。
     */
    @Data
    public static class Retry {
        /** 最大尝试次数（含首次） */
        private int maxAttempts = 5;
        /** 退避基数（秒） */
        private int baseBackoffSeconds = 60;
        /** 退避上限（秒） */
        private int backoffCapSeconds = 3600;
        /** 每轮最多处理条数 */
        private int batchSize = 50;
    }

    @Data
    public static class Rocketmq {

        /** 免打扰时使用的最小延迟级别（越大越晚；RocketMQ 默认 1~18） */
        private int quietDelayLevel = 10;

        /** 去重窗口（秒）；同一用户同一模板在窗口内只发一次，<=0 关闭 */
        private int dedupWindowSeconds = 60;

        /** 是否启用 RocketMQ（关闭时通知仅落库待发送） */
        private boolean enabled = true;

        /** Name Server 地址 */
        private String nameServer = "localhost:9876";

        /** 生产者组 */
        private String producerGroup = "yunanexus-notice-producer";

        /** 消费者组 */
        private String consumerGroup = "yunanexus-notice-consumer";

        /** 通知主题 */
        private String topic = "YUNA_NOTICE_TOPIC";

        /** 通知标签（消费订阅用） */
        private String tag = "notice";

        /** 延迟等级（RocketMQ 固定 18 级：1s 5s 10s 30s 1m...），<=0 表示不延迟 */
        private int delayLevel = 3;

        /** 发送超时（毫秒） */
        private int sendTimeoutMillis = 3000;
    }

    @Data
    public static class Mail {

        /** 发件人地址（缺省时由 JavaMailSender 的默认 from 决定） */
        private String from;

        /** 发件人显示名 */
        private String fromName = "YunaNexus";
    }
}
