package cc.nanoic.yunanexus.meta.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.meta.config.IdSegmentProperties;
import cc.nanoic.yunanexus.meta.domain.SegmentBuffer;
import cc.nanoic.yunanexus.meta.entity.IdSegment;
import cc.nanoic.yunanexus.meta.mapper.IdSegmentMapper;
import cc.nanoic.yunanexus.meta.metrics.IdSegmentMetrics;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 号段分配服务（生产级）。<p>
 *
 * 基于 Leaf-segment 双 Buffer 模式：<br>
 * 1. 从 DB 一次性领取号段 [start, end]（乐观锁 + version）；<br>
 * 2. 内存双 Buffer 分发，当前号段使用率达到阈值后异步预加载下一号段；<br>
 * 3. 当前号段耗尽无缝切换到预备号段；<br>
 * 4. 预备号段未就绪时同步兜底加载，并明确报错（不静默失败）。<p>
 *
 * 额外生产级特性：<br>
 * - <b>动态步长</b>：按上一号段的消耗速率反推下一号段步长（期望缓冲时长 targetBufferSeconds），
 *   并在 [minStep, maxStep] 内平滑收敛；<br>
 * - <b>降级</b>：连续异步预加载失败达到阈值后进入降级，停止异步打库，改由同步兜底，成功后自动恢复；<br>
 * - <b>监控埋点</b>：发放量 / 领取量 / 失败数 / 冲突数 / 剩余量 / 步长 / 降级状态 上报 Micrometer。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class IdSegmentService {

    private final IdSegmentMapper idSegmentMapper;
    private final IdSegmentProperties properties;
    private final IdSegmentMetrics metrics;
    private final Clock clock;

    private final Map<String, DoubleBufferHolder> bufferMap = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public IdSegmentService(IdSegmentMapper idSegmentMapper,
                            IdSegmentProperties properties,
                            IdSegmentMetrics metrics,
                            Clock clock) {
        this.idSegmentMapper = idSegmentMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
        this.executor = Executors.newFixedThreadPool(properties.getAsyncLoaderThreads(), r -> {
            Thread t = new Thread(r, "id-segment-loader");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 获取下一个 ID
     *
     * @param bizTag 业务标识
     * @return 全局唯一、趋势递增的 Long 型 ID
     */
    public long nextId(String bizTag) {
        DoubleBufferHolder holder = bufferMap.computeIfAbsent(bizTag, DoubleBufferHolder::new);
        return holder.nextId();
    }

    /**
     * 各 bizTag 的降级状态，供健康检查使用
     *
     * @return bizTag -> 是否降级
     */
    public Map<String, Boolean> degradedStatus() {
        Map<String, Boolean> status = new ConcurrentHashMap<>();
        bufferMap.forEach((tag, holder) -> status.put(tag, holder.degraded.get()));
        return status;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * 双重缓冲持有器
     */
    private class DoubleBufferHolder {

        private final String bizTag;

        /** 当前正在对外提供服务的号段 */
        private volatile SegmentBuffer current;

        /** 预备号段，由异步线程提前加载，切换时直接顶上 */
        private volatile SegmentBuffer next;

        /** 是否正在异步加载（防止重复提交） */
        private volatile boolean isLoading = false;

        /** 连续异步预加载失败次数 */
        private final AtomicInteger consecutiveAsyncFailures = new AtomicInteger(0);

        /** 是否已进入降级状态 */
        private final AtomicBoolean degraded = new AtomicBoolean(false);

        DoubleBufferHolder(String bizTag) {
            this.bizTag = bizTag;
            // 首次同步加载：保证该 bizTag 要么可用，要么立刻暴露错误
            this.current = loadNewSegment(properties.getDefaultStep());
            // 剩余量以抓取时读取，避免热路径每发一号都写一次 gauge
            metrics.registerRemaining(bizTag, () -> current == null ? 0L : current.remaining());
        }

        synchronized long nextId() {
            long id = current.nextId();
            if (id != -1) {
                metrics.recordIssued(bizTag);
                if (current.usedRatio() >= properties.getPreloadThreshold()) {
                    asyncLoadNext();
                }
                return id;
            }

            // 当前号段已耗尽，尝试切换到预备号段
            if (next != null && !next.isExhausted()) {
                current = next;
                next = null;
                long switched = current.nextId();
                metrics.recordIssued(bizTag);
                asyncLoadNext();
                return switched;
            }

            // 极端情况：预备号段尚未就绪（首次加载异常、异步失败、降级等），同步兜底
            log.warn("bizTag={} 无可用号段，降级为同步加载", bizTag);
            current = loadNewSegment(computeNextStep(current));
            next = null;
            consecutiveAsyncFailures.set(0);
            degraded.set(false);
            metrics.markDegraded(bizTag, false);
            long fallback = current.nextId();
            metrics.recordIssued(bizTag);
            asyncLoadNext();
            return fallback;
        }

        /**
         * 从数据库领取一个新号段。使用乐观锁（version）保证多实例并发安全
         *
         * @param step 期望步长（动态步长计算结果）
         * @return 新号段对应的 SegmentBuffer
         */
        private SegmentBuffer loadNewSegment(int step) {
            int effectiveStep = clampStep(step);
            try {
                for (int retry = 0; retry < properties.getLoadRetryTimes(); retry++) {
                    IdSegment row = idSegmentMapper.selectById(bizTag);
                    if (row == null) {
                        throw new BizException(ErrorCode.SEGMENT_NOT_FOUND, bizTag);
                    }

                    long oldMaxId = row.getMaxId();
                    int oldVersion = row.getVersion();
                    long newMaxId = oldMaxId + effectiveStep;

                    int updated = idSegmentMapper.updateMaxId(bizTag, oldVersion, newMaxId, effectiveStep);
                    if (updated == 1) {
                        long newStart = oldMaxId + 1;
                        metrics.recordLoadSuccess(bizTag, effectiveStep);
                        log.info("加载新号段 bizTag={} 范围=[{}, {}] 步长={}", bizTag, newStart, newMaxId, effectiveStep);
                        return new SegmentBuffer(newStart, newMaxId, effectiveStep, clock.millis());
                    }

                    metrics.recordConflict(bizTag);
                    log.warn("号段领取乐观锁冲突 bizTag={} 重试={}", bizTag, retry + 1);
                }
                throw new BizException(ErrorCode.SEGMENT_OPTIMISTIC_LOCK_FAILED, bizTag);
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("号段加载失败 bizTag={}", bizTag, e);
                throw new BizException(ErrorCode.SEGMENT_LOAD_FAILED, bizTag);
            }
        }

        /**
         * 动态步长：按上一号段的消耗速率反推目标步长（期望缓冲 targetBufferSeconds），
         * 并与上一段步长加权平滑，最终钳制在 [minStep, maxStep]。
         */
        private int computeNextStep(SegmentBuffer consumed) {
            long elapsed = clock.millis() - consumed.getLoadTime();
            if (elapsed < 1000) {
                elapsed = 1000; // 防瞬时耗尽导致速率异常放大
            }
            double ratePerSec = (double) consumed.getStep() / (elapsed / 1000.0);
            double targetStep = ratePerSec * properties.getTargetBufferSeconds();
            double smooth = properties.getStepSmoothFactor();
            int next = (int) Math.round(consumed.getStep() * (1 - smooth) + targetStep * smooth);
            return clampStep(next);
        }

        private int clampStep(int step) {
            return Math.max(properties.getMinStep(), Math.min(properties.getMaxStep(), step));
        }

        private void asyncLoadNext() {
            if (isLoading) {
                return;
            }
            if (next != null && !next.isExhausted()) {
                return;
            }
            if (degraded.get()) {
                // 降级态停止异步打库，交由同步兜底；同步成功后自动恢复
                return;
            }
            isLoading = true;
            try {
                executor.submit(() -> {
                    try {
                        SegmentBuffer loaded = loadNewSegment(computeNextStep(current));
                        next = loaded;
                        consecutiveAsyncFailures.set(0);
                        log.info("异步预加载号段成功 bizTag={} 范围=[{}, {}]", bizTag, loaded.getStart(), loaded.getEnd());
                    } catch (BizException e) {
                        onAsyncLoadFailure(e);
                    } finally {
                        isLoading = false;
                    }
                });
            } catch (RejectedExecutionException e) {
                isLoading = false;
                log.warn("异步预加载线程池饱和 bizTag={}，本次跳过，交由同步兜底", bizTag);
            }
        }

        private void onAsyncLoadFailure(BizException e) {
            int failures = consecutiveAsyncFailures.incrementAndGet();
            metrics.recordLoadFailure(bizTag);
            log.error("异步预加载号段失败 bizTag={} 连续失败={} 错误码={}", bizTag, failures, e.getCode());
            if (failures >= properties.getMaxConsecutiveAsyncFailures()
                    && degraded.compareAndSet(false, true)) {
                metrics.markDegraded(bizTag, true);
                log.error("bizTag={} 连续异步预加载失败 {} 次，进入降级状态", bizTag, failures);
            }
        }
    }
}