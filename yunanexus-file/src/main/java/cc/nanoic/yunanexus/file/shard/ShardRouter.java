package cc.nanoic.yunanexus.file.shard;

/**
 * 分片路由策略（可插拔）。<p>
 *
 * 由分片键（本模块为 {@code fileId}）决策逻辑分片编号（0 ~ logicalCount-1）。
 * 上传时先取号再路由，保证「元数据落库分片」与「内容存储键」一致。
 *
 * @author yunanexus
 */
public interface ShardRouter {

    /**
     * 为给定全局文件 ID 决策逻辑分片编号。
     *
     * @param fileId 全局内部文件 ID
     * @return 逻辑分片编号（0 ~ logicalCount-1）
     */
    int route(long fileId);
}
