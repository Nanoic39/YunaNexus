package cc.nanoic.yunanexus.meta.domain;

import lombok.Getter;
import lombok.ToString;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 号段内存缓冲区 <p>
 *
 * 用于封装一个从数据库领取到的号段 [start, end]，
 * 内部通过 AtomicLong 保证多线程并发取号时的线程安全。<p>
 *
 * 额外携带 {@code step}（号段长度）与 {@code loadTime}（加载时间），
 * 供动态步长按消耗速率反推下一号段的步长。该对象本身不持有业务逻辑，
 * 仅作为号段数据的容器和分配器使用。
 */
@Getter
@ToString
public class SegmentBuffer {

    /** 号段起始值（包含） */
    private final long start;

    /** 号段结束值（包含） */
    private final long end;

    /** 号段步长（即号段长度 end - start + 1），用于动态步长计算 */
    private final long step;

    /** 号段加载时间（毫秒），用于计算消耗速率 */
    private final long loadTime;

    /** 当前已分配到的值，初始等于 start - 1，表示尚未分配 */
    private final AtomicLong current;

    /**
     * 构造一个号段缓冲区
     *
     * @param start    号段起始值（包含）
     * @param end      号段结束值（包含）
     * @param step     号段步长（用于动态步长计算）
     * @param loadTime 号段加载时间（毫秒）
     */
    public SegmentBuffer(long start, long end, long step, long loadTime) {
        if (start > end) {
            throw new IllegalArgumentException(
                    String.format("Invalid segment range: start=%d, end=%d", start, end));
        }
        this.start = start;
        this.end = end;
        this.step = step;
        this.loadTime = loadTime;
        // 初始化为 start - 1，这样第一次 getAndIncrement 后返回 start
        this.current = new AtomicLong(start - 1);
    }

    /**
     * 从当前号段中获取下一个 ID。<p>
     *
     * @return 下一个可用 ID；如果号段已耗尽，返回 -1
     */
    public long nextId() {
        long id = current.incrementAndGet();
        if (id > end) {
            return -1L;
        }
        return id;
    }

    /**
     * 判断当前号段是否已耗尽
     */
    public boolean isExhausted() {
        return current.get() >= end;
    }

    /**
     * 当前号段剩余可用 ID 数量
     */
    public long remaining() {
        long used = current.get() - start + 1;
        long total = end - start + 1;
        long remaining = total - used;
        return Math.max(remaining, 0);
    }

    /**
     * 当前号段总容量
     */
    public long capacity() {
        return end - start + 1;
    }

    /**
     * 使用比例（0.0 ~ 1.0），用于判断是否触发异步预加载
     */
    public double usedRatio() {
        long total = capacity();
        if (total == 0) {
            return 1.0;
        }
        long used = current.get() - start + 1;
        return (double) used / total;
    }
}