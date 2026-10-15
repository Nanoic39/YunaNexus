package cc.nanoic.yunanexus.file.domain;

import java.util.List;

/**
 * 分片上传初始化结果。
 *
 * @param uploadId        上传会话 ID（秒传命中时为 {@code null}）
 * @param instantUpload  是否秒传命中（命中则无需再上传分片）
 * @param uploadedChunks 已上传分片下标（断点续传时非空）
 * @param file           秒传命中的文件元数据（未命中时为 {@code null}）
 * @author yunanexus
 */
public record ChunkInitResult(String uploadId,
                              boolean instantUpload,
                              List<Integer> uploadedChunks,
                              FileView file) {
}
