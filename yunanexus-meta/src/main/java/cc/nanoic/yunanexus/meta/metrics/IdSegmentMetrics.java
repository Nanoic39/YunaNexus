package cc.nanoic.yunanexus.meta.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 号段服务监控指标埋点。<p>
 *
 * 统一封装号段服务的 Micrometer 指标，指标名统一以 {@code yunanexus.segment} 为前缀，
 * 以 {@code biz_tag} 为维度标签，交由 Actuator + Prometheus 抓取。<p>
 *
 * 性能约定：<br>
 * - 发放量等高频计数使用缓存后的 {@link Counter}（LongAdder，热路径开销极低）；<br>
 * - 剩余量使用「抓取时读取」的 {@link Gauge}，不在每个 ID 上写值，避免百万 QPS 场景的性能损耗。
 *
 * @author yunanexus
 */
@Component
public class IdSegmentMetrics {

    /** 指标名前缀 */
    private static final String PREFIX = "yunanexus.segment";

    private final MeterRegistry registry;

    /** 高频计数器缓存，避免每次取号都做一次注册表查找 */
    private final Map<String, Counter> issued = new ConcurrentHashMap<>();
    private final Map<String, Counter> loadSuccess = new ConcurrentHashMap<>();
    private final Map<String, Counter> loadFailure = new ConcurrentHashMap<>();
    private final Map<String, Counter> conflict = new ConcurrentHashMap<>();

    /** 值型指标，仅在状态变化时写入 */
    private final Map<String, AtomicLong> stepGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> degradedGauges = new ConcurrentHashMap<>();

    public IdSegmentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 记录一次 ID 发放 */
    public void recordIssued(String bizTag) {
        issued.computeIfAbsent(bizTag, k -> counter("issued_total", k)).increment();
    }

    /** 记录一次号段领取成功，并更新当前步长 */
    public void recordLoadSuccess(String bizTag, int step) {
        loadSuccess.computeIfAbsent(bizTag, k -> counter("load_total", k)).increment();
        stepGauges.computeIfAbsent(bizTag, k -> gauge("step", k)).set(step);
    }

    /** 记录一次号段领取失败 */
    public void recordLoadFailure(String bizTag) {
        loadFailure.computeIfAbsent(bizTag, k -> counter("load_failure_total", k)).increment();
    }

    /** 记录一次乐观锁冲突 */
    public void recordConflict(String bizTag) {
        conflict.computeIfAbsent(bizTag, k -> counter("optimistic_lock_conflict_total", k)).increment();
    }

    /** 记录降级状态（1 降级 / 0 正常） */
    public void markDegraded(String bizTag, boolean degraded) {
        degradedGauges.computeIfAbsent(bizTag, k -> gauge("degraded", k)).set(degraded ? 1 : 0);
    }

    /** 注册剩余量 Gauge：抓取时通过 supplier 读取当前号段剩余，避免热路径写值 */
    public void registerRemaining(String bizTag, Supplier<Long> supplier) {
        Gauge.builder(PREFIX + ".remaining", supplier, s -> s.get().doubleValue())
                .tag("biz_tag", bizTag)
                .register(registry);
    }

    private Counter counter(String name, String bizTag) {
        return Counter.builder(PREFIX + "." + name)
                .tag("biz_tag", bizTag)
                .register(registry);
    }

    private AtomicLong gauge(String name, String bizTag) {
        AtomicLong value = new AtomicLong();
        Gauge.builder(PREFIX + "." + name, value, AtomicLong::doubleValue)
                .tag("biz_tag", bizTag)
                .register(registry);
        return value;
    }
}