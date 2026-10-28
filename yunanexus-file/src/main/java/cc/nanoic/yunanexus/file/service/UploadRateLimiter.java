package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上传限流（A4）。<p>
 *
 * 用固定窗口计数而不是引入 Redis/Sentinel：限流对象是"单实例的上传入口"，
 * 本地计数足够且没有网络开销；将来多实例部署时把计数换成 Redis INCR 即可，
 * 接口不变。
 *
 * <p>两个维度同时限：<b>按用户</b>防止单人刷爆，<b>按 IP</b>防止换账号绕过。
 * 只限上传类接口——下载与删除不应被限，否则用户连清理都做不了。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class UploadRateLimiter {

    /** 窗口长度（毫秒） */
    private static final long WINDOW_MILLIS = 60_000L;

    private final FileProperties properties;

    /** key → 当前窗口计数 */
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public UploadRateLimiter(FileProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验并累加一次上传请求。
     *
     * @param userId 用户 ID
     * @param ip     客户端 IP
     * @throws BizException 超出速率时抛出 {@link ErrorCode#FILE_UPLOAD_RATE_LIMITED}
     */
    public void acquire(long userId, String ip) {
        FileProperties.RateLimit limit = properties.getRateLimit();
        if (!limit.isEnabled()) {
            return;
        }
        tick("u:" + userId, limit.getPerUserPerMinute());
        if (ip != null && !ip.isBlank()) {
            tick("ip:" + ip, limit.getPerIpPerMinute());
        }
    }

    private void tick(String key, int maxPerMinute) {
        if (maxPerMinute <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Counter counter = counters.compute(key, (k, current) -> {
            if (current == null || now - current.windowStart >= WINDOW_MILLIS) {
                return new Counter(now);
            }
            return current;
        });
        int used = counter.count.incrementAndGet();
        if (used > maxPerMinute) {
            throw new BizException(ErrorCode.FILE_UPLOAD_RATE_LIMITED,
                    "上传过于频繁：" + used + " 次/分钟，上限 " + maxPerMinute + " 次/分钟");
        }
        // 顺手清理过期窗口，避免 map 无界增长（低频、均摊）
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= WINDOW_MILLIS);
        }
    }

    /** 单 key 的固定窗口计数 */
    private static final class Counter {
        private final long windowStart;
        private final AtomicInteger count = new AtomicInteger();

        private Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
