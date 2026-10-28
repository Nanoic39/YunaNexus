package cc.nanoic.yunanexus.file.task;

import cc.nanoic.yunanexus.file.config.FileProperties;
import cc.nanoic.yunanexus.file.config.ShardContextHolder;
import cc.nanoic.yunanexus.file.mapper.FileMetaMapper;
import cc.nanoic.yunanexus.file.service.FileQuotaService;
import cc.nanoic.yunanexus.file.shard.ShardRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文件模块维护任务：配额对账 + 补扫调度。
 *
 * <p>为什么需要它：
 * <ul>
 *   <li><b>对账</b>：配额是"增减维护"的计数，进程崩溃、异常回滚都会让它漂移。
 *       漂移方向如果是少算，就是可被利用的漏洞，所以必须定期用真实数据校正。</li>
 *   <li><b>补扫</b>：扫描服务不可用时上传的文件停在 {@code pending}，
 *       没有调度它们就会永远停在那里——"放行但遗忘"和直接放行没有区别。</li>
 * </ul>
 *
 * @author yunanexus
 */
@Slf4j
@Component
public class FileMaintenanceTask {

    private final FileMetaMapper fileMetaMapper;
    private final FileQuotaService quotaService;
    private final ShardRouter shardRouter;
    private final FileProperties properties;

    public FileMaintenanceTask(FileMetaMapper fileMetaMapper, FileQuotaService quotaService,
                               ShardRouter shardRouter, FileProperties properties) {
        this.fileMetaMapper = fileMetaMapper;
        this.quotaService = quotaService;
        this.shardRouter = shardRouter;
        this.properties = properties;
    }

    /**
     * 配额对账（默认每日 04:10）：逐物理库聚合真实用量并回写配额。
     */
    @Scheduled(cron = "${yunanexus.file.quota.reconcile-cron:0 10 4 * * ?}")
    public void reconcileQuota() {
        if (!properties.getQuota().isReconcileEnabled()) {
            return;
        }
        int corrected = 0;
        for (int shard = 0; shard < properties.getShard().getPhysicalCount(); shard++) {
            ShardContextHolder.set(shard);
            try {
                List<Map<String, Object>> rows = fileMetaMapper.aggregateUsage();
                for (Map<String, Object> row : rows) {
                    long userId = ((Number) row.get("userId")).longValue();
                    long count = ((Number) row.get("fileCount")).longValue();
                    long bytes = ((Number) row.get("usedBytes")).longValue();
                    quotaService.correct(userId, bytes, count);
                    corrected++;
                }
            } catch (RuntimeException error) {
                log.warn("配额对账失败 shard={}：{}", shard, error.toString());
            } finally {
                ShardContextHolder.clear();
            }
        }
        log.info("配额对账完成，校正 {} 个用户", corrected);
    }

    /**
     * 补扫调度（默认每 30 分钟）：把超期未扫描的文件挑出来。
     *
     * <p>当前没有接真实扫描服务，所以只做"发现与上报"：超过保留期仍未被扫描的文件
     * 会被记录为 {@code unscanned_expired} 告警日志，由采集链路进入审计/告警。
     * 接入扫描服务后在这里发起扫描请求即可，调度骨架不用改。
     */
    @Scheduled(cron = "${yunanexus.file.scan.rescan-cron:0 */30 * * * ?}")
    public void dispatchRescan() {
        FileProperties.Scan scan = properties.getScan();
        if (!scan.isEnabled()) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusMinutes(scan.getPendingTimeoutMinutes());
        int found = 0;
        for (int shard = 0; shard < properties.getShard().getPhysicalCount(); shard++) {
            ShardContextHolder.set(shard);
            try {
                List<Long> fileIds = fileMetaMapper.selectPendingScan(before, scan.getBatchSize());
                found += fileIds.size();
                if (!fileIds.isEmpty()) {
                    log.warn("发现 {} 个超期未扫描文件（shard={}），样例 {}", fileIds.size(), shard,
                            fileIds.subList(0, Math.min(5, fileIds.size())));
                }
            } catch (RuntimeException error) {
                log.warn("补扫调度失败 shard={}：{}", shard, error.toString());
            } finally {
                ShardContextHolder.clear();
            }
        }
        if (found > 0) {
            log.warn("SCAN_PENDING_EXPIRED 待扫描积压 {} 个文件，检查扫描服务是否可用", found);
        }
    }
}
