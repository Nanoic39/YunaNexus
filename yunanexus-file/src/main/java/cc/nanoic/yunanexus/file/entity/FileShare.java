package cc.nanoic.yunanexus.file.entity;
import lombok.Data;
import java.time.LocalDateTime;
/** 分享链接的能力标识与提取码摘要，均不进入访问日志。 */
@Data
public class FileShare {
    private String shareId;
    private Long userId;
    private Long fileId;
    private String fileName;
    private String codeHash;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Integer revoked;
    private Integer failedAttempts;
    private LocalDateTime failureWindow;
}
