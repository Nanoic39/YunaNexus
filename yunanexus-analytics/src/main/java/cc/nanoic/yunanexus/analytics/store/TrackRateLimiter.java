package cc.nanoic.yunanexus.analytics.store;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 埋点限流（跨实例共享计数）。
 *
 * <p>用库内原子 UPSERT 而不是内存计数：多实例部署时内存计数各自为政，
 * 实际限额会随实例数线性放大，限流等于没做。
 *
 * @author yunanexus
 */
public class TrackRateLimiter {

    private final JdbcTemplate jdbcTemplate;

    public TrackRateLimiter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 累加本秒事件数并返回累计值。
     *
     * @param windowSecond 当前秒
     * @param batchSize    本批事件数
     * @return 本秒累计事件数
     */
    public long accumulate(long windowSecond, int batchSize) {
        jdbcTemplate.update("INSERT INTO track_rate (window_second, hits) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE hits = hits + ?", windowSecond, batchSize, batchSize);
        Long hits = jdbcTemplate.queryForObject(
                "SELECT hits FROM track_rate WHERE window_second = ?", Long.class, windowSecond);
        // 顺带清理两分钟前的窗口，避免表无界增长（低频）
        if (windowSecond % 60 == 0) {
            jdbcTemplate.update("DELETE FROM track_rate WHERE window_second < ?", windowSecond - 120);
        }
        return hits == null ? batchSize : hits;
    }
}
