package cc.nanoic.yunanexus.user.shard;

import cc.nanoic.yunanexus.user.config.UserProperties;
import org.springframework.stereotype.Component;

/**
 * 取模分片路由（默认实现，roadmap 5.7）。<p>
 *
 * 以 {@code userId % logicalCount} 决策逻辑分片。号段模式生成的 userId 趋势递增且连续，
 * 取模后天然均匀分布、确定性可复现，且与认证中心 {@code ModShardAllocator} 语义一致，
 * 保证「外部 ID 编码所用分片」与「用户资料落库分片」始终相同。
 *
 * @author yunanexus
 */
@Component
public class ModShardRouter implements ShardRouter {

    /** 外部 ID 分片位上限（4 bit） */
    private static final int MAX_SHARD_COUNT = 16;

    private final int logicalCount;

    public ModShardRouter(UserProperties properties) {
        int count = properties.getShard().getLogicalCount();
        if (count < 1 || count > MAX_SHARD_COUNT) {
            throw new IllegalStateException(
                    "yunanexus.user.shard.logical-count 必须在 1~" + MAX_SHARD_COUNT + " 之间，当前: " + count);
        }
        this.logicalCount = count;
    }

    @Override
    public int route(long userId) {
        if (userId < 0) {
            throw new IllegalArgumentException("userId 不能为负数: " + userId);
        }
        return (int) (userId % logicalCount);
    }
}
