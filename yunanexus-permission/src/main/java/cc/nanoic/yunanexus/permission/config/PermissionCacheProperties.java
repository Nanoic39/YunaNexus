package cc.nanoic.yunanexus.permission.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 鉴权结果缓存配置（前缀 {@code yunanexus.permission.cache}）。
 *
 * @author yunanexus
 */
@Data
@ConfigurationProperties(prefix = "yunanexus.permission.cache")
public class PermissionCacheProperties {

    /** 是否启用鉴权结果缓存 */
    private boolean enabled = true;

    /** 缓存存活时间（秒） */
    private long ttlSeconds = 60;

    /** 最大条目数（超出后整体清空，避免无界增长） */
    private int maxSize = 10000;
}
