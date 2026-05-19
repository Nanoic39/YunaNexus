package cc.nanoic.yunanexus.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "yunanexus.file.avatar")
public class AvatarUploadProperties {
    private long maxSizeBytes = 5 * 1024 * 1024L;
    private List<String> allowedContentTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private List<String> allowedExtensions = List.of(
            "jpg",
            "jpeg",
            "png",
            "webp"
    );
}