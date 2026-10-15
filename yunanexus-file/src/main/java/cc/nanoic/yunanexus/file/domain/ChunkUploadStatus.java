package cc.nanoic.yunanexus.file.domain;

import java.util.List;

/**
 * 分片上传进度（用于断点续传）。
 *
 * @param uploadId        上传会话 ID
 * @param totalChunks     分片总数
 * @param uploadedChunks  已上传分片下标（升序）
 * @author yunanexus
 */
public record ChunkUploadStatus(String uploadId,
                                int totalChunks,
                                List<Integer> uploadedChunks) {
}
