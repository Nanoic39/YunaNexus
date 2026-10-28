package cc.nanoic.yunanexus.file.service;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.file.config.FileProperties;
import cc.nanoic.yunanexus.file.mapper.UploadRateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 上传限流（A4）。
 *
 * <p><b>为什么用数据库而不是内存计数</b>：内存计数在多实例部署时每个实例各算一份，
 * 实际每秒上限会随实例数线性放大——限流形同虚设。改为库内共享计数后，
 * 无论几个实例，全局限额一致。
 *
 * <p><b>为什么不用 Redis</b>：file 服务当前没有 Redis 依赖，为限流引入连接池与
 * 一整套运维面不划算；MySQL 的原子 UPSERT 足以承担"每分钟每用户几十次"的量级。
 *
 * <p>两个维度同时限：按用户防单人刷爆，按 IP 防换账号绕过。
 * 只作用于上传类接口——下载与删除不限，否则用户连清理都做不了。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class UploadRateLimiter {

    /** 窗口长度（毫秒） */
    private static final long WINDOW_MILLIS = 60_000L;

    private final FileProperties properties;
    private final UploadRateMapper rateMapper;

    public UploadRateLimiter(FileProperties properties, UploadRateMapper rateMapper) {
        this.properties = properties;
        this.rateMapper = rateMapper;
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
        long windowStart = (System.currentTimeMillis() / WINDOW_MILLIS) * WINDOW_MILLIS;
        tick("u:" + userId, windowStart, limit.getPerUserPerMinute());
        if (ip != null && !ip.isBlank()) {
            tick("ip:" + ip, windowStart, limit.getPerIpPerMinute());
        }
        // 清理两分钟前的窗口（低频、均摊），避免表无界增长
        if (System.nanoTime() % 100 == 0) {
            rateMapper.purgeBefore(windowStart - 2 * WINDOW_MILLIS);
        }
    }

    private void tick(String key, long windowStart, int maxPerMinute) {
        if (maxPerMinute <= 0) {
            return;
        }
        rateMapper.increment(key, windowStart);
        Integer hits = rateMapper.selectHits(key, windowStart);
        int used = hits == null ? 1 : hits;
        if (used > maxPerMinute) {
            throw new BizException(ErrorCode.FILE_UPLOAD_RATE_LIMITED,
                    "上传过于频繁：" + used + " 次/分钟，上限 " + maxPerMinute + " 次/分钟");
        }
    }
}
