package cc.nanoic.yunanexus.file.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分片上传初始化请求。<p>
 *
 * {@code sha256} 与 {@code totalSize} 同时提供时，服务端会进行内容查重，
 * 命中则直接秒传（不生成上传会话）。
 *
 * @author yunanexus
 */
@Data
public class ChunkInitRequest {

    /** 原始文件名（含扩展名） */
    @NotBlank
    private String fileName;

    /** 分片总数（从 1 开始计数） */
    @Min(1)
    private int totalChunks;

    /** 文件总字节数（用于秒传查重与大小限制校验） */
    private long totalSize;

    /** MIME 类型（可选） */
    private String contentType;

    /** 内容 SHA-256（可选，用于秒传查重） */
    private String sha256;
}
