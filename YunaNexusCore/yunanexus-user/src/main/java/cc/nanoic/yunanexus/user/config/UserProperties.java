package cc.nanoic.yunanexus.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "yunanexus.user.route")
public class UserProperties {
    private int version = 0;
}
