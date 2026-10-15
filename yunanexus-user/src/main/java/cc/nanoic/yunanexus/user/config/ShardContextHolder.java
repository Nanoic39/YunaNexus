package cc.nanoic.yunanexus.user.config;

/**
 * 分片路由上下文（ThreadLocal，roadmap 5.4）。<p>
 *
 * 存放当前线程待访问的逻辑分片编号，由 {@code ShardRoutingAspect} 在进入带
 * {@code @ShardRoute} 的方法时写入、退出时清除；{@link ShardRoutingDataSource}
 * 据此选择物理库。
 *
 * @author yunanexus
 */
public final class ShardContextHolder {

    private static final ThreadLocal<Integer> SHARD_INDEX = new ThreadLocal<>();

    private ShardContextHolder() {
    }

    public static void set(int shardIndex) {
        SHARD_INDEX.set(shardIndex);
    }

    public static Integer get() {
        return SHARD_INDEX.get();
    }

    public static void clear() {
        SHARD_INDEX.remove();
    }
}
