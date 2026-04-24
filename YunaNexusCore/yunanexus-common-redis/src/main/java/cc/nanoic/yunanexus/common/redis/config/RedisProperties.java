package cc.nanoic.yunanexus.common.redis.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "yunanexus.redis")
public class RedisProperties {
    private boolean enabled = true;
    private String host = "localhost";
    private int port = 6379;
    private int database = 0;
    private String username;
    private String password;
    private boolean ssl = false;
    private Duration timeout = Duration.ofSeconds(5);
}
