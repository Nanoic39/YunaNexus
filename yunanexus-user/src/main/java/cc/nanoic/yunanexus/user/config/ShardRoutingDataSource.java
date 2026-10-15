package cc.nanoic.yunanexus.user.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 分库路由数据源（roadmap 5.3）。<p>
 *
 * 依据 {@link ShardContextHolder} 中的逻辑分片编号选择物理库：
 * {@code physicalIndex = shardIndex % physicalCount}。
 * 无路由上下文时直接抛错，避免误落库（fail-fast）。
 *
 * @author yunanexus
 */
public class ShardRoutingDataSource extends AbstractRoutingDataSource implements DisposableBean {

    private final int physicalCount;

    private final List<DataSource> delegates = new ArrayList<>();

    public ShardRoutingDataSource(int physicalCount) {
        if (physicalCount < 1) {
            throw new IllegalArgumentException("physicalCount 必须 >= 1，当前: " + physicalCount);
        }
        this.physicalCount = physicalCount;
    }

    /** 记录物理库数据源，供容器关闭时释放连接池 */
    public void addDelegate(DataSource dataSource) {
        delegates.add(dataSource);
    }

    /** 返回各物理库数据源（供健康探活逐个检查） */
    public List<DataSource> getDelegates() {
        return List.copyOf(delegates);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        Integer shardIndex = ShardContextHolder.get();
        if (shardIndex == null) {
            throw new IllegalStateException("缺少分片路由上下文：请通过带 @ShardRoute 的方法访问分库数据");
        }
        return shardIndex % physicalCount;
    }

    @Override
    public void destroy() {
        for (DataSource dataSource : delegates) {
            if (dataSource instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // 关闭阶段忽略单个连接池释放异常
                }
            }
        }
    }
}
