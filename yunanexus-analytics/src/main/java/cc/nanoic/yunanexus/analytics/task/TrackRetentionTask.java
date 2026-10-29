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
        int batch = Math.max(1, properties.getRetention().getBatchSize());
        long total = 0;
        // 分级规则：更精确的类型优先匹配，未命中的走全局保留期
        for (var rule : properties.getRetention().getRules()) {
            if (rule.getEventTypePrefix() == null || rule.getDays() <= 0) {
                continue;
            }
            LocalDateTime before = LocalDateTime.now().minusDays(rule.getDays());
            while (true) {
                int deleted = eventStore.purgeBeforeByTypePrefix(rule.getEventTypePrefix(), before, batch);
                total += deleted;
                if (deleted < batch) {
                    break;
                }
            }
            log.info("分级清理 {} 前缀：{} 天前数据", rule.getEventTypePrefix(), rule.getDays());
        }
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        while (true) {
            int deleted = eventStore.purgeBefore(before, batch);
            total += deleted;
            if (deleted < batch) {
                break;
            }
        }
        log.info("埋点保留清理完成：删除 {} 条（默认早于 {}）", total, before);
    }
}
