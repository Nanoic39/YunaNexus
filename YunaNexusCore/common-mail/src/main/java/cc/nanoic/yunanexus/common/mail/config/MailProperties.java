package cc.nanoic.yunanexus.common.mail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "yunanexus.mail")
public class MailProperties {
    private String host;
    private int port;
    private String username;
    private String password;
    private String from; // FromAddress，需要和username保持一致
    private String fromName; // 外显来源名称
    private boolean auth = true;
    private boolean starttls = true;
}
