package cc.nanoic.yunanexus.analytics.task;

import cc.nanoic.yunanexus.analytics.config.AnalyticsProperties;
import cc.nanoic.yunanexus.analytics.store.EventStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 埋点保留策略（默认 90 天）。
 *
 * <p>埋点是增长最快的表；不设保留期，几个月后既拖慢查询也把磁盘吃满。
 * 分批删除以避免长事务锁表。
 *
 * @author yunanexus
 */
@Slf4j
@Component
public class TrackRetentionTask {

    private final EventStore eventStore;
    private final AnalyticsProperties properties;

    public TrackRetentionTask(EventStore eventStore, AnalyticsProperties properties) {
        this.eventStore = eventStore;
        this.properties = properties;
    }

    @Scheduled(cron = "${yunanexus.analytics.retention.cron:0 20 4 * * ?}")
    public void purgeExpired() {
        int days = properties.getRetention().getDays();
        if (days <= 0) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        int batch = Math.max(1, properties.getRetention().getBatchSize());
        long total = 0;
        while (true) {
            int deleted = eventStore.purgeBefore(before, batch);
            total += deleted;
            if (deleted < batch) {
                break;
            }
        }
        log.info("埋点保留清理完成：删除 {} 条（早于 {}）", total, before);
    }
}
