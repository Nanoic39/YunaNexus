package cc.nanoic.yunanexus.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redisson 分布式锁连接配置。<p>
 *
 * 直接绑定 {@code spring.data.redis.*}（与 Spring Boot Redis 自动装配同源），
 * 用于构建 Redisson 客户端（ADR-016）。这样避免依赖 Spring Boot 内部
 * {@code RedisProperties} 的包路径（Boot 4.0 存在包迁移），配置项与
 * {@code spring.data.redis} 完全一致。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedissonProperties {

    /** Redis 主机 */
    private String host = "localhost";

    /** Redis 端口 */
    private int port = 6379;

    /** Redis 密码（可选，本地默认无） */
    private String password;

    /** Redis 逻辑库 */
    private int database = 0;
}
