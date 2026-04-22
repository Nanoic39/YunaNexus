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
    private String fromName = "【YunaNexus】";
    private String subjectPrefix = "";

    private Map<String, TemplateConfig> templates = new HashMap<>();
    private Map<String, TemplateConfig> customTemplates = new HashMap<>();

    @Data
    public static class TemplateConfig {
        private String subject;
        private String body;
    }
}
