package cc.nanoic.yunanexus.audit.shard;

/**
 * 分片路由策略（可插拔）。<p>
 *
 * 由分片键（本模块为 {@code userId}）决策逻辑分片编号（0 ~ logicalCount-1）。
 * 审计日志落库前先按操作者 user_id 路由，保证同一用户的日志落在同一分片；
 * {@code userId = 0}（匿名/未认证）统一落 0 号分片。
 *
 * @author yunanexus
 */
public interface ShardRouter {

    /**
     * 为给定用户 ID 决策逻辑分片编号。
     *
     * @param userId 操作者用户 ID（0 表示匿名/未认证）
     * @return 逻辑分片编号（0 ~ logicalCount-1）
     */
    int route(long userId);
}
