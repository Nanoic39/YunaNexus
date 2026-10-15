package cc.nanoic.yunanexus.auth.task;

import cc.nanoic.yunanexus.auth.config.RegisterOutboxProperties;
import cc.nanoic.yunanexus.auth.domain.RegisterOutboxReconciliation;
import cc.nanoic.yunanexus.auth.mapper.AuthRegisterOutboxMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;

/**
 * 每日核对认证库注册记录并输出告警及指标（ADR-021）。
 * 只读执行，不自动修复数据、不调用尚未实现的用户中心。
 * 指标读取内存快照，Prometheus 抓取不会触发数据库查询。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yunanexus.auth.outbox", name = "reconciliation-enabled", havingValue = "true")
public class RegisterOutboxReconciliationTask {

    private static final String PREFIX = "yunanexus.auth.outbox.reconciliation";

    private final AuthRegisterOutboxMapper mapper;
    private final RegisterOutboxProperties properties;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(null, 0, -1, -1));
    private final Counter successes;
    private final Counter failures;
    private final Counter alerts;

    /** 创建任务并注册低基数指标；首次执行前数量、执行状态与告警状态均为 -1（未知）。 */
    public RegisterOutboxReconciliationTask(AuthRegisterOutboxMapper mapper,
                                            RegisterOutboxProperties properties,
                                            Clock clock,
                                            MeterRegistry registry) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
        registerCount(registry, "pending", RegisterOutboxReconciliation::getPending);
        registerCount(registry, "failed", RegisterOutboxReconciliation::getFailed);
        registerCount(registry, "overdue", RegisterOutboxReconciliation::getOverdue);
        registerCount(registry, "missing", RegisterOutboxReconciliation::getMissing);
        registerCount(registry, "orphan", RegisterOutboxReconciliation::getOrphan);
        registerCount(registry, "mismatch", RegisterOutboxReconciliation::getMismatch);
        Gauge.builder(PREFIX + ".last.success.timestamp.seconds", snapshot,
                ref -> ref.get().lastSuccess()).register(registry);
        Gauge.builder(PREFIX + ".last.run.success", snapshot,
                ref -> ref.get().lastRunSuccess()).register(registry);
        Gauge.builder(PREFIX + ".alert", snapshot,
                ref -> ref.get().anomaly()).register(registry);
        successes = registry.counter(PREFIX + ".runs", "result", "success");
        failures = registry.counter(PREFIX + ".runs", "result", "failure");
        alerts = registry.counter(PREFIX + ".alerts");
    }

    /** 启用后首次延迟一分钟执行，之后每次完成后间隔一天执行。 */
    @Scheduled(initialDelayString = "${yunanexus.auth.outbox.reconciliation-initial-delay-ms:60000}",
            fixedDelayString = "${yunanexus.auth.outbox.reconciliation-delay-ms:86400000}")
    public void reconcile() {
        try {
            RegisterOutboxReconciliation result = Objects.requireNonNull(mapper.reconcile(
                    LocalDateTime.now(clock).minusSeconds(properties.getReconciliationOverdueSeconds())));
            boolean anomaly = result.hasAnomalies();
            snapshot.set(new Snapshot(result, clock.instant().getEpochSecond(), 1, anomaly ? 1 : 0));
            successes.increment();
            if (anomaly) {
                alerts.increment();
                // 稳定告警标记 [ALERT]：日志系统按此关键字配置告警规则。
                log.error("[ALERT] 注册对账异常 pending={} failed={} overdue={} missing={} orphan={} mismatch={}",
                        result.getPending(), result.getFailed(), result.getOverdue(),
                        result.getMissing(), result.getOrphan(), result.getMismatch());
            } else {
                log.info("注册对账完成 pending={}，无失败、超时或本地数据差异", result.getPending());
            }
        } catch (Exception e) {
            // 查询失败不覆盖上一次有效数量，不将数据库不可用误报为零差异。
            snapshot.updateAndGet(previous -> new Snapshot(previous.counts(), previous.lastSuccess(), 0, previous.anomaly()));
            failures.increment();
            // 下游异常消息可能包含连接信息；日志仅记录类型。
            log.error("[ALERT] 注册对账执行失败 type={}，保留上次成功快照", e.getClass().getSimpleName());
        }
    }

    private void registerCount(MeterRegistry registry, String kind,
                               ToLongFunction<RegisterOutboxReconciliation> extractor) {
        Gauge.builder(PREFIX + ".records", snapshot, ref -> {
            RegisterOutboxReconciliation counts = ref.get().counts();
            return counts == null ? -1 : extractor.applyAsLong(counts);
        }).tag("kind", kind).register(registry);
    }

    private record Snapshot(RegisterOutboxReconciliation counts, long lastSuccess, int lastRunSuccess, int anomaly) { }
}

