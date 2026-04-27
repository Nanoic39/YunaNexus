package cc.nanoic.yunanexus.common.rocketmq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yunanexus.rocketmq")
public class RocketMQProperties {
    private boolean enabled = true;
    private long sendTimeoutMs = 3000L;
    private int sendRetryTimes = 3;
}
