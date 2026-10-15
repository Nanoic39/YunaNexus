package cc.nanoic.yunanexus.file.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分片上传会话元数据。<p>
 *
 * 由服务层序列化为 JSON 存入存储后端的会话文件（{@code chunks/{uploadId}/session.json}），
 * 用于分片校验、断点续传与合并时补齐元数据。
 *
 * @author yunanexus
 */
@Data
public class UploadSession {

    /** 上传会话 ID */
    private String uploadId;

    /** 原始文件名 */
    private String fileName;

    /** 扩展名（小写、不含点） */
    private String ext;

    /** MIME 类型 */
    private String contentType;

    /** 分片总数 */
    private int totalChunks;

    /** 文件总字节数 */
    private long totalSize;

    /** 上传者用户 ID */
    private long userId;

    /** 所属部门 ID */
    private Long deptId;

    /** 预分配的全局文件 ID */
    private Long fileId;

    /** 逻辑分片编号（0~15） */
    private Integer shardIndex;

    /** 会话创建时间（用于过期清理） */
    private LocalDateTime createTime;
}
