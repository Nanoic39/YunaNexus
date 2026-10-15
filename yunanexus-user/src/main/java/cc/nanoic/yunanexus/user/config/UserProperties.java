package cc.nanoic.yunanexus.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户中心配置（前缀 {@code yunanexus.user}）。<p>
 *
 * 分库连接信息在此显式声明（不使用 {@code spring.datasource}），由
 * {@link DynamicDataSourceConfig} 按物理库数构建多个数据源。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.user")
public class UserProperties {

    private Shard shard = new Shard();

    private Datasource datasource = new Datasource();

    /**
     * 手机号功能开关：控制「资料自助绑定 / 换绑手机号」。
     * 默认关闭（当前无 SMS 能力，无法校验手机号归属）；待短信通道就绪后置 true 重开，代码零改动。
     */
    private boolean mobileEnabled = false;

    /** 分片参数 */
    @Data
    public static class Shard {
        /** 逻辑分片总数（1~16，须与认证中心一致） */
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
        private String databasePrefix = "yunanexus_user_";
        private String username = "root";
        private String password = "";
        private String urlParams =
                "serverTimezone=Asia/Shanghai&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true";
        private String poolNamePrefix = "user-hikari-";
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 3000;
    }
}
