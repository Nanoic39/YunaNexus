package cc.nanoic.yunanexus.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计中心配置（前缀 {@code yunanexus.audit}）。<p>
 *
 * 分库连接信息在此显式声明（不使用 {@code spring.datasource}），由
 * {@link DynamicDataSourceConfig} 按物理库数构建多个数据源；
 * RocketMQ 用于日志异步落库，归档参数控制按月分区滚动与冷备。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.audit")
public class AuditProperties {

    private Shard shard = new Shard();

    private Datasource datasource = new Datasource();

    private Rocketmq rocketmq = new Rocketmq();

    private Archive archive = new Archive();

    /** 外部锚定配置 */
    private Anchor anchor = new Anchor();

    /** 高危动作通知配置 */
    private Risk risk = new Risk();

    /** 分片参数 */
    @Data
    public static class Shard {
        /** 逻辑分片总数（1~16） */
        private int logicalCount = 16;
        /** 物理库数量（逻辑分片按 shardIndex % physicalCount 映射） */
        private int physicalCount = 2;
    }

    /** 分库连接参数 */
    /**
     * 高危动作实时通知：webhook 投递与限流。
     */
    @Data
    public static class Risk {

        /** 是否启用 */
        private boolean enabled = true;

        /** webhook 地址；留空则降级为结构化日志（AUDIT_RISK 标记） */
        private String webhookUrl = "";

        /** 同一规则在多少秒内只发一次（防刷屏） */
        private int windowSeconds = 60;

        /** 连接超时（毫秒） */
        private int timeoutMillis = 2000;
    }

    /**
     * 外部锚定：链头落库外文件的目录与开关。
     */
    @Data
    public static class Anchor {

        /** 锚定目录（须在审计库之外，建议放只读/可 WORM 的位置） */
        private String dir = "/YunaNexus/platform/backups/audit-anchor";

        /** 是否启用 */
        private boolean enabled = true;
    }

    @Data
    public static class Datasource {
        private String host = "localhost";
        private int port = 3306;
        /** 物理库名前缀（实际库名 = 前缀 + 物理库索引） */
        private String databasePrefix = "yunanexus_audit_";
        private String username = "root";
        private String password = "";
        private String urlParams =
                "serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true";
        private String poolNamePrefix = "audit-hikari-";
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 3000;
    }

    /** RocketMQ 异步落库参数 */
    @Data
    public static class Rocketmq {
        /** 是否启用（本地无 MQ 时可关闭） */
        private boolean enabled = true;
        private String nameServer = "localhost:9876";
        private String producerGroup = "audit-producer-group";
        private String consumerGroup = "audit-consumer-group";
        private String topic = "YUNA_AUDIT_TOPIC";
        private String tag = "audit";
        private long sendTimeoutMillis = 3000;
    }

    /** 归档参数（按月分区滚动删除 + 导出冷备） */
    @Data
    public static class Archive {
        /** 保留月数，超期分区滚动删除并冷备 */
        private int retentionMonths = 36;
        /** 提前预建的未来分区月数 */
        private int preCreateMonths = 2;
        /** 冷备导出根目录 */
        private String backupDir = "./data/audit-archive";
        /** 归档任务是否启用 */
        private boolean enabled = true;
    }
}
