package cc.nanoic.yunanexus.auth.service;

/**
 * 分片决策器（可插拔，roadmap 5.7）。<p>
 *
 * 注册时决定用户的逻辑分片编号（0~15），用于外部 ID 编码与用户中心路由。
 * 阶段 5 用户中心落地前由认证中心决策；落地后可替换为「用户中心决策」实现，
 * 调用方（{@code RegisterService}）无需改动。
 *
 * @author yunanexus
 */
public interface ShardAllocator {

    /**
     * 为给定全局用户 ID 决策逻辑分片编号。
     *
     * @param userId 全局内部用户 ID
     * @return 分片编号（0 ~ shardCount-1）
     */
    int allocate(long userId);
}
