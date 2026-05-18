package cc.nanoic.yunanexus.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yunanexus.file.storage.local")
public class FileStorageProperties {
    private String rootPath;
}