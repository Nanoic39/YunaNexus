package cc.nanoic.yunanexus.meta.service;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.meta.config.IdSegmentProperties;
import cc.nanoic.yunanexus.meta.entity.IdSegment;
import cc.nanoic.yunanexus.meta.mapper.IdSegmentMapper;
import cc.nanoic.yunanexus.meta.metrics.IdSegmentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IdSegmentService} 单元测试。<p>
 *
 * 通过「有状态的假号段存储」模拟数据库乐观锁语义（CAS 推进 max_id / version），
 * 覆盖：趋势递增唯一、耗尽兜底、配置不存在、乐观锁冲突重试/耗尽、动态步长、降级与恢复。
 *
 * @author yunanexus
 */
class IdSegmentServiceTest {

    private IdSegmentMapper mapper;
    private FakeSegmentStore store;
    private MutableClock clock;
    private final List<IdSegmentService> services = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mapper = mock(IdSegmentMapper.class);
        store = new FakeSegmentStore();
        clock = new MutableClock(1_000_000_000_000L);
        when(mapper.selectById(any())).thenAnswer(inv -> store.select());
        when(mapper.updateMaxId(any(), anyInt(), anyLong(), anyInt()))
                .thenAnswer(inv -> store.cas(
                        inv.getArgument(1, Integer.class),
                        inv.getArgument(2, Long.class),
                        inv.getArgument(3, Integer.class)));
    }

    @AfterEach
    void tearDown() {
        services.forEach(IdSegmentService::shutdown);
    }

    /** 构造一个关闭异步预加载、便于确定性测试的服务（preloadThreshold 置为 2.0，永不触发异步） */
    private IdSegmentService newSyncService(IdSegmentProperties props) {
        props.setPreloadThreshold(2.0);
        IdSegmentService service = new IdSegmentService(mapper, props, metrics(), clock);
        services.add(service);
        return service;
    }

    private IdSegmentMetrics metrics() {
        return new IdSegmentMetrics(new SimpleMeterRegistry());
    }

    private IdSegmentProperties defaultProps() {
        IdSegmentProperties p = new IdSegmentProperties();
        p.setMinStep(100);
        p.setMaxStep(10000);
        p.setDefaultStep(100);
        p.setTargetBufferSeconds(900);
        p.setStepSmoothFactor(0.5);
        p.setLoadRetryTimes(3);
        p.setAsyncLoaderThreads(2);
        p.setMaxConsecutiveAsyncFailures(2);
        return p;
    }

    @Test
    void 趋势递增且唯一() {
        store.step = 100;
        IdSegmentService service = newSyncService(defaultProps());

        long prev = 0;
        for (int i = 0; i < 100; i++) {
            long id = service.nextId("user");
            assertTrue(id > prev, "ID 应趋势递增");
            prev = id;
        }
        assertEquals(100L, prev);
    }

    @Test
    void 号段耗尽后同步兜底仍单调() {
        store.step = 100;
        IdSegmentService service = newSyncService(defaultProps());

        for (int i = 0; i < 100; i++) {
            service.nextId("user");
        }
        // 第 101 个 ID 应来自下一号段（同步兜底加载），且与上一段连续
        assertEquals(101L, service.nextId("user"));
    }

    @Test
    void 号段配置不存在应拒绝() {
        store.exists = false;
        IdSegmentService service = newSyncService(defaultProps());

        BizException e = assertThrows(BizException.class, () -> service.nextId("unknown"));
        assertEquals(10001, e.getCode());
    }

    @Test
    void 乐观锁冲突重试后成功() {
        store.step = 100;
        store.conflictOnce = true;
        IdSegmentService service = newSyncService(defaultProps());

        assertEquals(1L, service.nextId("user"));
        assertEquals(1, store.version.get(), "首次冲突后重试成功，version 应推进一次");
    }

    @Test
    void 乐观锁冲突重试耗尽应拒绝() {
        store.step = 100;
        store.alwaysConflict = true;
        IdSegmentService service = newSyncService(defaultProps());

        BizException e = assertThrows(BizException.class, () -> service.nextId("user"));
        assertEquals(10003, e.getCode());
    }

    @Test
    void 高速消耗时动态步长增长() {
        store.step = 1000;
        IdSegmentProperties props = defaultProps();
        props.setMinStep(1000);
        props.setDefaultStep(1000);
        IdSegmentService service = newSyncService(props);

        // 消费整段 1000 个 ID
        for (int i = 0; i < 1000; i++) {
            service.nextId("user");
        }
        // 模拟这 1000 个 ID 在 1 秒内消耗完毕，触发下一号段动态步长计算
        clock.advance(1000);
        long next = service.nextId("user");

        assertEquals(1001L, next);
        // 速率 1000/s × 目标缓冲 900s 远超 maxStep，应被钳制到 maxStep=10000
        assertEquals(10000, store.step, "高速消耗下步长应增长到 maxStep");
    }

    @Test
    void 连续异步失败进入降级_同步恢复() throws InterruptedException {
        store.step = 100;
        IdSegmentProperties props = defaultProps();
        props.setPreloadThreshold(0.0); // 首次取号即触发异步预加载
        props.setMaxConsecutiveAsyncFailures(1);
        IdSegmentService service = new IdSegmentService(mapper, props, metrics(), clock);
        services.add(service);

        // 构造时同步加载成功；此后异步加载一律失败（模拟 DB 不可用）
        store.failAsync = true;

        for (int i = 0; i < 100; i++) {
            service.nextId("user");
        }
        // 异步线程连续失败达到阈值后进入降级
        assertTrue(await(() -> Boolean.TRUE.equals(service.degradedStatus().get("user"))),
                "连续异步失败后应进入降级");

        // DB 恢复，号段耗尽时同步兜底加载成功并自动退出降级
        store.failAsync = false;
        for (int i = 0; i < 100; i++) {
            service.nextId("user");
        }
        assertTrue(await(() -> Boolean.FALSE.equals(service.degradedStatus().get("user"))),
                "同步兜底成功后应退出降级");
    }

    private boolean await(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    /** 可控时钟，用于动态步长速率计算的确定性测试 */
    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(long millis) {
            this.instant = Instant.ofEpochMilli(millis);
        }

        void advance(long millis) {
            this.instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** 有状态的假号段存储，模拟数据库乐观锁（CAS 推进 max_id / version） */
    private static class FakeSegmentStore {
        final AtomicLong maxId = new AtomicLong(0);
        final AtomicInteger version = new AtomicInteger(0);
        volatile int step = 100;
        volatile boolean exists = true;
        volatile boolean conflictOnce = false;
        volatile boolean alwaysConflict = false;
        volatile boolean failAsync = false;

        IdSegment select() {
            if (!exists) {
                return null;
            }
            IdSegment row = new IdSegment();
            row.setBizTag("user");
            row.setMaxId(maxId.get());
            row.setVersion(version.get());
            row.setStep(step);
            return row;
        }

        int cas(int oldVersion, long newMaxId, int newStep) {
            // 仅异步预加载线程（name 前缀 id-segment-loader）模拟 DB 不可用，
            // 主线程的同步加载/兜底不受影响，从而可确定性地区分「异步失败」与「同步恢复」。
            if (failAsync && Thread.currentThread().getName().startsWith("id-segment-loader")) {
                throw new RuntimeException("模拟异步预加载时 DB 不可用");
            }
            synchronized (this) {
                if (alwaysConflict) {
                    return 0;
                }
                if (conflictOnce) {
                    conflictOnce = false;
                    return 0;
                }
                if (version.get() != oldVersion) {
                    return 0;
                }
                maxId.set(newMaxId);
                version.incrementAndGet();
                step = newStep;
                return 1;
            }
        }
    }
}