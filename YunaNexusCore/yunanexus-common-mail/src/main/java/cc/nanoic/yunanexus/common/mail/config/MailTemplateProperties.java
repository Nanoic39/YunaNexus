package cc.nanoic.yunanexus.common.mail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "yunanexus.mail")
public class MailTemplateProperties {
    private boolean enabled = true;
    private String fromAddress;
    private String fromName = "【YunaNexus Dev.GROUP】";
    private String subjectPrefix = "";
    private String host = "smtp.qq.com";
    private Integer port = 465;
    private String username;
    private String password;
    private String protocol = "smtp";
    private Map<String, Object> properties = new HashMap<>();
}
