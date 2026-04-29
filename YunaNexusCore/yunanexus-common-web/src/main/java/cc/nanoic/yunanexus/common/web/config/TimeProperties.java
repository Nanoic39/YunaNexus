package cc.nanoic.yunanexus.common.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "custom.time")
public class TimeProperties {
    /**
     * 业务侧配置时区，默认 Asia/Shanghai
     */
    private String timeZone = "Asia/Shanghai";
}
