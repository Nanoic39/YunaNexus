package cc.nanoic.yunanexus.file.shard;

import cc.nanoic.yunanexus.file.config.FileProperties;
import org.springframework.stereotype.Component;

/**
 * 取模分片路由（默认实现）。<p>
 *
 * 以 {@code fileId % logicalCount} 决策逻辑分片。号段模式生成的 fileId 趋势递增且连续，
 * 取模后天然均匀分布、确定性可复现。
 *
 * @author yunanexus
 */
@Component
public class ModShardRouter implements ShardRouter {

    /** 逻辑分片数上限（4 bit） */
    private static final int MAX_SHARD_COUNT = 16;

    private final int logicalCount;

    public ModShardRouter(FileProperties properties) {
        int count = properties.getShard().getLogicalCount();
        if (count < 1 || count > MAX_SHARD_COUNT) {
            throw new IllegalStateException(
                    "yunanexus.file.shard.logical-count 必须在 1~" + MAX_SHARD_COUNT + " 之间，当前: " + count);
        }
        this.logicalCount = count;
    }

    @Override
    public int route(long fileId) {
        if (fileId < 0) {
            throw new IllegalArgumentException("fileId 不能为负数: " + fileId);
        }
        return (int) (fileId % logicalCount);
    }
}
