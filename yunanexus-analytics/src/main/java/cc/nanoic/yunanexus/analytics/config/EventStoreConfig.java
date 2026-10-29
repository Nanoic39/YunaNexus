package cc.nanoic.yunanexus.analytics.config;

import cc.nanoic.yunanexus.analytics.store.EventStore;
import cc.nanoic.yunanexus.analytics.store.MysqlEventStore;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 埋点存储装配。
 *
 * <p>使用<b>独立数据源</b>接入 MySQL，而不是复用 {@code spring.datasource}：
 * 分析模块原本的配置指向 ClickHouse，混用会让两者互相踩。
 * 将来切 ClickHouse 时只需换一个 {@link EventStore} 实现。
 *
 * @author yunanexus
 */
@Slf4j
@Configuration
public class EventStoreConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource eventStoreDataSource(
            @Value("${yunanexus.analytics.event-store.url:jdbc:mysql://mysql:3306/yunanexus_analytics}") String url,
            @Value("${yunanexus.analytics.event-store.username:root}") String username,
            @Value("${yunanexus.analytics.event-store.password:}") String password,
            @Value("${yunanexus.analytics.event-store.maximum-pool-size:4}") int poolSize) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("analytics-event-store");
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(Math.max(1, poolSize));
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(3000);
        log.info("埋点存储数据源已装配: {}", url);
        return dataSource;
    }

    /** 查询专用 JdbcTemplate：与分析写入共用同一数据源，但语义上分离便于日后切 ClickHouse */
    @Bean
    public JdbcTemplate analyticsJdbcTemplate(HikariDataSource eventStoreDataSource) {
        return new JdbcTemplate(eventStoreDataSource);
    }

    @Bean
    public cc.nanoic.yunanexus.analytics.store.TrackRateLimiter trackRateLimiter(
            HikariDataSource eventStoreDataSource) {
        return new cc.nanoic.yunanexus.analytics.store.TrackRateLimiter(
                new JdbcTemplate(eventStoreDataSource));
    }

    @Bean
    public EventStore eventStore(HikariDataSource eventStoreDataSource,
                                 tools.jackson.databind.ObjectMapper objectMapper) {
        return new MysqlEventStore(new JdbcTemplate(eventStoreDataSource), objectMapper);
    }
}
