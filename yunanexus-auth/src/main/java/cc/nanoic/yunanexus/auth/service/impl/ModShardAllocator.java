package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.ShardProperties;
import cc.nanoic.yunanexus.auth.service.ShardAllocator;
import org.springframework.stereotype.Service;

/**
 * 取模分片决策器（默认实现）。<p>
 *
 * 以 {@code userId % shardCount} 决策分片。号段模式生成的 userId 趋势递增且连续，
 * 取模后天然轮询分布，16 分片下分布均匀、确定性可复现、无额外依赖。
 * 外部 ID 编码使用 4 bit 分片位（0~15），故分片总数上限为 16。
 *
 * @author yunanexus
 */
@Service
public class ModShardAllocator implements ShardAllocator {

    /** 外部 ID 分片位上限（4 bit） */
    private static final int MAX_SHARD_COUNT = 16;

    private final int shardCount;

    public ModShardAllocator(ShardProperties properties) {
        int count = properties.getShardCount();
        if (count < 1 || count > MAX_SHARD_COUNT) {
            throw new IllegalStateException(
                    "yunanexus.auth.shard.shard-count 必须在 1~" + MAX_SHARD_COUNT + " 之间，当前: " + count);
        }
        this.shardCount = count;
    }

    @Override
    public int allocate(long userId) {
        if (userId < 0) {
            throw new IllegalArgumentException("userId 不能为负数: " + userId);
        }
        return (int) (userId % shardCount);
    }
}
