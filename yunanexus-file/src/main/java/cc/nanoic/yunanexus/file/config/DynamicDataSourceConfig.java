package cc.nanoic.yunanexus.file.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态数据源装配。<p>
 *
 * 按 {@code physicalCount} 构建各物理库的 Hikari 连接池，注册进
 * {@link ShardRoutingDataSource} 作为唯一 {@link DataSource} Bean；
 * Spring Boot 的 {@code DataSourceAutoConfiguration} 因已存在 DataSource 而自动退让。
 *
 * @author yunanexus
 */
@Configuration
public class DynamicDataSourceConfig {

    @Bean
    public DataSource dataSource(FileProperties properties) {
        FileProperties.Shard shard = properties.getShard();
        int physicalCount = shard.getPhysicalCount();
        if (physicalCount < 1 || physicalCount > shard.getLogicalCount()) {
            throw new IllegalStateException(
                    "yunanexus.file.shard.physical-count 必须在 1~logical-count 之间，当前: " + physicalCount);
        }

        Map<Object, Object> targets = new LinkedHashMap<>();
        ShardRoutingDataSource routing = new ShardRoutingDataSource(physicalCount);
        for (int index = 0; index < physicalCount; index++) {
            DataSource physical = buildPhysicalDataSource(properties.getDatasource(), index);
            targets.put(index, physical);
            routing.addDelegate(physical);
        }
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(targets.get(0));
        return routing;
    }

    private DataSource buildPhysicalDataSource(FileProperties.Datasource ds, int index) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(ds.getPoolNamePrefix() + index);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + ds.getHost() + ":" + ds.getPort()
                + "/" + ds.getDatabasePrefix() + index + "?" + ds.getUrlParams());
        config.setUsername(ds.getUsername());
        config.setPassword(ds.getPassword());
        config.setMaximumPoolSize(ds.getMaximumPoolSize());
        config.setMinimumIdle(ds.getMinimumIdle());
        config.setConnectionTimeout(ds.getConnectionTimeout());
        return new HikariDataSource(config);
    }
}
