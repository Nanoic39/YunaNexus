package cc.nanoic.yunanexus.user.shard;

/**
 * 分片路由策略（可插拔，roadmap 5.7）。<p>
 *
 * 由 {@code userId} 决策逻辑分片编号（0 ~ logicalCount-1），须与认证中心注册时的
 * 分片决策保持一致，否则用户资料会落到与外部 ID 编码不一致的分片。
 *
 * @author yunanexus
 */
public interface ShardRouter {

    /**
     * 为给定全局用户 ID 决策逻辑分片编号。
     *
     * @param userId 全局内部用户 ID
     * @return 逻辑分片编号（0 ~ logicalCount-1）
     */
    int route(long userId);
}
