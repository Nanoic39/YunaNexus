package cc.nanoic.yunanexus.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "yunanexus.file")
public class FileProperties {
    private String storagePath = "./storage";
}