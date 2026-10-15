package cc.nanoic.yunanexus.file.task;

import cc.nanoic.yunanexus.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分片上传临时目录过期清理任务（roadmap 7.2 存储增强）。<p>
 *
 * 周期扫描存储后端的分片上传会话，清理超过 {@code yunanexus.file.chunk.session-ttl-seconds}
 * 未完成的临时分片与会话元数据，避免孤儿文件长期占用磁盘。
 *
 * @author yunanexus
 */
@Component
public class ChunkCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ChunkCleanupTask.class);

    private final FileService fileService;

    public ChunkCleanupTask(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 周期清理过期分片上传会话；启动后延迟 1 分钟首次执行，之后按固定间隔执行。
     */
    @Scheduled(initialDelayString = "60000",
            fixedDelayString = "${yunanexus.file.chunk.cleanup-interval-ms:3600000}")
    public void cleanupExpiredChunkUploads() {
        try {
            int removed = fileService.cleanupExpiredChunkUploads();
            if (removed > 0) {
                log.info("分片上传过期清理任务完成，共清理 {} 个会话", removed);
            }
        } catch (RuntimeException e) {
            log.warn("分片上传过期清理任务执行失败: {}", e.getMessage());
        }
    }
}
