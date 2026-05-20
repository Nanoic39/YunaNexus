package cc.nanoic.yunanexus.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yunanexus.file.upload")
public class FileUploadProperties {
    private long directUploadThresholdBytes = 500L * 1024 * 1024;
    private long normalUserMaxFileSizeBytes = 2L * 1024 * 1024 * 1024;
    private long normalUserMaxSpaceBytes = 50L * 1024 * 1024 * 1024;
    private long chunkSizeBytes = 16L * 1024 * 1024;
    private String tempRootPath = "./storage/yunanexus-file-temp";
}