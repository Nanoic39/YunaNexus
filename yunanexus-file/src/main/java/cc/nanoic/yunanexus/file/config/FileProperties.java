package cc.nanoic.yunanexus.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 文件中心配置（前缀 {@code yunanexus.file}）。<p>
 *
 * 分库连接信息在此显式声明（不使用 {@code spring.datasource}），由
 * {@link DynamicDataSourceConfig} 按物理库数构建多个数据源。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.file")
public class FileProperties {

    private Shard shard = new Shard();

    private Datasource datasource = new Datasource();

    private Storage storage = new Storage();

    private Upload upload = new Upload();

    private Chunk chunk = new Chunk();

    private Quota quota = new Quota();

    private RateLimit rateLimit = new RateLimit();

    private Scan scan = new Scan();

    private Trash trash = new Trash();

    private Version version = new Version();

    private Thumbnail thumbnail = new Thumbnail();

    /** 版本保留策略 */
    @Data
    public static class Version {
        /** 每个逻辑文件保留的版本数（含最新版） */
        private int keep = 5;
    }

    /** 缩略图 */
    @Data
    public static class Thumbnail {
        /** 是否启用按需生成 */
        private boolean enabled = true;
        /** 默认边长（像素） */
        private int defaultSize = 256;
        /** 允许的最大边长，防超大请求打爆 CPU/内存 */
        private int maxSize = 1024;
    }

    /** 回收站 */
    @Data
    public static class Trash {
        /** 是否启用过期清理 */
        private boolean enabled = true;
        /** 保留天数（默认 30 天） */
        private int retentionDays = 30;
        /** 每轮最多处理条数 */
        private int batchSize = 200;
    }

    /** 恶意文件扫描（A5） */
    @Data
    public static class Scan {
        /** 是否启用补扫调度 */
        private boolean enabled = true;
        /** 视为"超期未扫描"的分钟数 */
        private long pendingTimeoutMinutes = 1440;
        /** 每轮最多取多少条 */
        private int batchSize = 200;
        /** 扫描引擎：clamav / none */
        private String engine = "clamav";
        /** clamd 主机（同 compose 网络内为服务名） */
        private String clamdHost = "clamav";
        /** clamd 端口 */
        private int clamdPort = 3310;
        /** 单次扫描超时（毫秒） */
        private int timeoutMillis = 10_000;
    }

    /** 上传限流（A4）：用户与 IP 双维度，固定 1 分钟窗口 */
    @Data
    public static class RateLimit {
        /** 是否启用 */
        private boolean enabled = true;
        /** 单用户每分钟上传次数上限 */
        private int perUserPerMinute = 60;
        /** 单 IP 每分钟上传次数上限 */
        private int perIpPerMinute = 120;
    }

    /** 存储配额（单用户默认值；file_quota 表可按用户覆盖） */
    @Data
    public static class Quota {
        /** 默认容量上限（5 GiB）；超出只拒绝新上传，不删已有文件 */
        private long defaultLimitBytes = 5L * 1024 * 1024 * 1024;
        /** 默认文件数上限（50 个）；后续可按角色/权限动态放宽 */
        private long defaultLimitFiles = 50L;
        /** 是否启用对账（关闭则配额纯靠增减维护，不推荐） */
        private boolean reconcileEnabled = true;
    }

    /** 分片参数 */
    @Data
    public static class Shard {
        /** 逻辑分片总数（1~16） */
        private int logicalCount = 16;
        /** 物理库数量（逻辑分片按 shardIndex % physicalCount 映射） */
        private int physicalCount = 2;
    }

    /** 分库连接参数 */
    @Data
    public static class Datasource {
        private String host = "localhost";
        private int port = 3306;
        /** 物理库名前缀（实际库名 = 前缀 + 物理库索引） */
        private String databasePrefix = "yunanexus_file_";
        private String username = "root";
        private String password = "";
        private String urlParams =
                "serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true";
        private String poolNamePrefix = "file-hikari-";
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 3000;
    }

    /** 文件存储参数（存储后端由 {@code type} 选择，当前仅实现 local） */
    @Data
    public static class Storage {
        /** 存储后端类型：local（预留 minio / oss） */
        private String type = "local";
        /** 本地存储根目录 */
        private String baseDir = "./data/file-storage";
    }

    /** 上传限制 */
    @Data
    public static class Upload {
        /** 单文件最大字节数（默认 20MB） */
        private long maxSize = 20L * 1024 * 1024;
        /**
         * 高危扩展名：无论扫描是否可用都拒绝。
         *
         * <p>扫描服务不可用时的 fail-open 策略只适用于"无法判定"的普通文件；
         * 可执行/脚本类属于"已知高风险"，不能因为扫描没开就放行。
         */
        private List<String> dangerousExtensions = List.of(
                "exe", "dll", "so", "bat", "cmd", "com", "scr", "msi", "vbs", "ps1",
                "sh", "bash", "php", "jsp", "asp", "aspx", "cgi", "jar", "war", "class");

        /** 允许的扩展名（小写、不含点）；为空表示不限制 */
        private List<String> allowedExtensions = List.of();
    }

    /** 分片上传 */
    @Data
    public static class Chunk {
        /** 单个分片最大字节数（服务端校验，防单请求过大；默认 20MB） */
        private long maxChunkSize = 20L * 1024 * 1024;
        /** 分片总数上限（默认 10000，约等于 200GB/20MB 分片；防稀疏下标 DoS） */
        private int maxChunks = 10_000;

        /** 分片文件总大小上限（默认 5GB） */
        private long maxSize = 5L * 1024 * 1024 * 1024;
        /** 上传会话过期时间（秒），超时后临时分片被清理任务回收（默认 24h） */
        private long sessionTtlSeconds = 24L * 3600;
        /** 过期清理任务执行间隔（毫秒，默认 1h） */
        private long cleanupIntervalMs = 3600_000;
    }
}
