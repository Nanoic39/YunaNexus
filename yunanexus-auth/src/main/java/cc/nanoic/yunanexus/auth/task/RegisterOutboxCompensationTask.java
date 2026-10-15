package cc.nanoic.yunanexus.auth.task;

import cc.nanoic.yunanexus.auth.client.UserProfileSyncClient;
import cc.nanoic.yunanexus.auth.config.RegisterOutboxProperties;
import cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox;
import cc.nanoic.yunanexus.auth.mapper.AuthRegisterOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 注册 outbox 补偿任务（ADR-021）。<p>
 *
 * 周期性扫描 {@code status=0} 且到期的 outbox 记录，调用用户中心创建用户资料：
 * <ul>
 *   <li>先幂等回查（{@code exists}）——已存在则直接置 DONE，避免重复创建；</li>
 *   <li>创建成功置 DONE；</li>
 *   <li>失败按指数退避安排下次重试，超过 {@code max-retry} 置 FAILED 并告警（error 日志）。</li>
 * </ul>
 *
 * <b>启用条件</b>：{@code yunanexus.auth.outbox.enabled=true}（默认开启）。
 *
 * @author yunanexus
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yunanexus.auth.outbox", name = "enabled", havingValue = "true")
public class RegisterOutboxCompensationTask {

    private final AuthRegisterOutboxMapper outboxMapper;
    private final UserProfileSyncClient userProfileSyncClient;
    private final RegisterOutboxProperties properties;
    private final Clock clock;

    public RegisterOutboxCompensationTask(AuthRegisterOutboxMapper outboxMapper,
                                          UserProfileSyncClient userProfileSyncClient,
                                          RegisterOutboxProperties properties,
                                          Clock clock) {
        this.outboxMapper = outboxMapper;
        this.userProfileSyncClient = userProfileSyncClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 每 60 秒执行一次补偿（首次延迟 30 秒，避开启动抖动）。
     */
    @Scheduled(initialDelayString = "30000", fixedDelayString = "60000")
    public void compensate() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AuthRegisterOutbox> pending = outboxMapper.selectPending(now, properties.getBatchSize());
        if (pending.isEmpty()) {
            return;
        }
        for (AuthRegisterOutbox record : pending) {
            processOne(record);
        }
    }

    private void processOne(AuthRegisterOutbox record) {
        long userId = record.getUserId();
        try {
            // 幂等回查：用户中心已存在则直接完成（ADR-021 幂等回查）
            if (userProfileSyncClient.exists(userId)) {
                outboxMapper.markDone(userId, LocalDateTime.now(clock));
                return;
            }
            userProfileSyncClient.createUserProfile(record);
            outboxMapper.markDone(userId, LocalDateTime.now(clock));
            log.info("注册 outbox 同步成功 userId={}", userId);
        } catch (Exception e) {
            int nextRetry = (record.getRetryCount() == null ? 0 : record.getRetryCount()) + 1;
            // 失败原因脱敏：仅记录异常类型，不落堆栈细节到 DB（避免泄露下游信息）
            String reason = e.getClass().getSimpleName();
            if (nextRetry >= properties.getMaxRetry()) {
                outboxMapper.markFailed(userId, nextRetry, reason);
                log.error("注册 outbox 同步失败超上限 userId={} 重试={} 类型={}", userId, nextRetry, reason);
            } else {
                long backoff = computeBackoffSeconds(nextRetry);
                outboxMapper.markRetry(userId, nextRetry,
                        LocalDateTime.now(clock).plusSeconds(backoff), reason);
                log.warn("注册 outbox 同步失败 userId={} 重试={} 下次退避={}s 类型={}",
                        userId, nextRetry, backoff, reason);
            }
        }
    }

    /**
     * 指数退避：initial * 2^(retry-1)，封顶 maxBackoffSeconds。
     */
    private long computeBackoffSeconds(int retry) {
        long initial = properties.getInitialBackoffSeconds();
        long max = properties.getMaxBackoffSeconds();
        int shift = Math.min(retry - 1, 30);
        long backoff = initial << shift;
        return backoff <= 0 || backoff > max ? max : backoff;
    }
}
