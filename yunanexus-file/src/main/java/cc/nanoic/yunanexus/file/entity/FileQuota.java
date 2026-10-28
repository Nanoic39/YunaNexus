package cc.nanoic.yunanexus.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户存储配额。
 *
 * <p>计数是显式维护的，不靠实时聚合：上传是高频路径，聚合会随文件量线性劣化。
 * 计数漂移由对账任务兜底（见 {@code FileQuotaService.reconcile}）。
 *
 * @author yunanexus
 */
@Data
@TableName("file_quota")
public class FileQuota {

    /** 用户 ID（分片键） */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 已用容量（字节） */
    private Long usedBytes;

    /** 已用文件数 */
    private Long fileCount;

    /** 容量上限；0 表示使用全局默认 */
    private Long limitBytes;

    /** 文件数上限；0 表示使用全局默认 */
    private Long limitFiles;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
