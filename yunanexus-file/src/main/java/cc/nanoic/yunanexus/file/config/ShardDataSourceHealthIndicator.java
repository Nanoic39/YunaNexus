package cc.nanoic.yunanexus.file.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * 分库健康指示器。<p>
 *
 * 逐个物理库探活并汇总：任一库不可用则整体 DOWN。因 {@link ShardRoutingDataSource}
 * 在无请求上下文时无法路由，默认 {@code db} 健康指示器已被关闭，由本类替代。
 *
 * @author yunanexus
 */
@Component
public class ShardDataSourceHealthIndicator implements HealthIndicator {

    private final List<DataSource> delegates;

    public ShardDataSourceHealthIndicator(DataSource dataSource) {
        this.delegates = dataSource instanceof ShardRoutingDataSource routing
                ? routing.getDelegates()
                : List.of(dataSource);
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        for (int index = 0; index < delegates.size(); index++) {
            String pool = "pool-" + index;
            try (Connection connection = delegates.get(index).getConnection()) {
                if (!connection.isValid(1)) {
                    builder.down().withDetail(pool, "invalid");
                }
            } catch (Exception e) {
                builder.down().withDetail(pool, e.getClass().getSimpleName());
            }
        }
        return builder.build();
    }
}
