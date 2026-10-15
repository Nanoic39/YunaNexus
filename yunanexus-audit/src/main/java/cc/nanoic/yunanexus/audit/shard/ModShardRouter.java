package cc.nanoic.yunanexus.audit.shard;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import org.springframework.stereotype.Component;

/**
 * 取模分片路由（默认实现）。<p>
 *
 * 以 {@code userId % logicalCount} 决策逻辑分片。userId = 0（匿名/未认证）落 0 号分片，
 * 其余按取模均匀分布、确定性可复现。
 *
 * @author yunanexus
 */
@Component
public class ModShardRouter implements ShardRouter {

    /** 逻辑分片数上限（4 bit） */
    private static final int MAX_SHARD_COUNT = 16;

    private final int logicalCount;

    public ModShardRouter(AuditProperties properties) {
        int count = properties.getShard().getLogicalCount();
        if (count < 1 || count > MAX_SHARD_COUNT) {
            throw new IllegalStateException(
                    "yunanexus.audit.shard.logical-count 必须在 1~" + MAX_SHARD_COUNT + " 之间，当前: " + count);
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
