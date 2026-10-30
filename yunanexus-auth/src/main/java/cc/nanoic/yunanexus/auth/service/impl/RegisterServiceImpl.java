package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.client.MetaIdClient;
import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.domain.EmailAddress;
import cc.nanoic.yunanexus.auth.domain.EmailCodePurpose;
import cc.nanoic.yunanexus.auth.domain.RegisterRequest;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.service.RegisterPersistenceService;
import cc.nanoic.yunanexus.auth.service.RegisterService;
import cc.nanoic.yunanexus.auth.service.ShardAllocator;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.core.util.SecureExternalIdGenerator;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 注册服务实现（ADR-021 最终一致补偿）。
 *
 * <p>注册流程：<br>
 * 1. Redisson 分布式锁（按 username，防并发重复创建，ADR-016）；<br>
 * 2. 唯一键预检（username / email / mobile）；<br>
 * 3. 调用 meta 领取全局内部用户 ID（Feign + Nacos）；<br>
 * 4. 分片决策（可插拔 {@link ShardAllocator}，默认取模，阶段 5 后由用户中心决策）；<br>
 * 5. 编码外部 ID（最新版本密钥）；<br>
 * 6. 同一本地事务写入 {@code auth_user} 与注册 outbox（{@link RegisterPersistenceService}）。
 *
 * <p><b>跨服务一致性</b>：用户资料同步（阶段 5）由 outbox + 定时补偿 + 幂等回查兜底（ADR-021）； outbox
 * 记录随注册同事务写入（durable），补偿任务在用户中心就绪后开启。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class RegisterServiceImpl implements RegisterService {

    /** 分布式锁 key 前缀 */
    private static final String LOCK_KEY = "auth:register:";

    /** 分布式锁等待时长（秒） */
    private static final long LOCK_WAIT_SECONDS = 5;

    /** 分布式锁租约时长（秒），进程崩溃自动过期 */
    private static final long LOCK_LEASE_SECONDS = 30;

    private final AuthUserMapper authUserMapper;
    private final MetaIdClient metaIdClient;
    private final SecureExternalIdGenerator externalIdGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RedissonClient redissonClient;
    private final Clock clock;
    private final ShardAllocator shardAllocator;
    private final RegisterPersistenceService registerPersistenceService;
    private final AuthProperties properties;
    private final EmailCodeService emailCodeService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private cc.nanoic.yunanexus.auth.task.RegisterOutboxCompensationTask registrationSync;

    public RegisterServiceImpl(
            AuthUserMapper authUserMapper,
            MetaIdClient metaIdClient,
            SecureExternalIdGenerator externalIdGenerator,
            PasswordEncoder passwordEncoder,
            RedissonClient redissonClient,
            Clock clock,
            ShardAllocator shardAllocator,
            RegisterPersistenceService registerPersistenceService,
            AuthProperties properties,
            EmailCodeService emailCodeService) {
        this.authUserMapper = authUserMapper;
        this.metaIdClient = metaIdClient;
        this.externalIdGenerator = externalIdGenerator;
        this.passwordEncoder = passwordEncoder;
        this.redissonClient = redissonClient;
        this.clock = clock;
        this.shardAllocator = shardAllocator;
        this.registerPersistenceService = registerPersistenceService;
        this.properties = properties;
        this.emailCodeService = emailCodeService;
    }

    @Override
    public String register(RegisterRequest request) {
        // Do not rely on controller validation: internal callers must not bypass proof of ownership.
        if (request == null || !hasText(request.getUsername())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "账号不能为空");
        }
        String email = EmailAddress.normalize(request.getEmail());
        if (email == null) throw new BizException(ErrorCode.BAD_REQUEST, "请提供有效邮箱");
        if (request.getEmailCode() == null || !request.getEmailCode().matches("[0-9]{6}")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请提供 6 位邮箱验证码");
        }
        String username = request.getUsername().trim();
        RLock lock = redissonClient.getLock(LOCK_KEY + username);
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.REGISTER_PROCESSING);
        }
        if (!locked) {
            throw new BizException(ErrorCode.REGISTER_PROCESSING);
        }
        try {
            return doRegister(request, username, email);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 实际注册逻辑（锁内执行）。 */
    private String doRegister(RegisterRequest request, String username, String email) {
        // 手机号功能默认关闭（无 SMS 能力无法校验归属）：注册不接受手机号 / 国家号
        if (!properties.isMobileEnabled()
                && (hasText(request.getMobile()) || hasText(request.getMobileCountry()))) {
            throw new BizException(ErrorCode.MOBILE_FEATURE_DISABLED);
        }
        // 唯一键预检：username / email / mobile
        if (authUserMapper.selectByUsername(username) != null) {
            throw new BizException(ErrorCode.REGISTER_USER_ALREADY_EXISTS);
        }
        if (authUserMapper.selectByEmail(email) != null) {
            throw new BizException(ErrorCode.REGISTER_USER_ALREADY_EXISTS);
        }
        String mobileCountry = resolveMobileCountry(request);
        if (request.getMobile() != null
                && !request.getMobile().isBlank()
                && authUserMapper.selectByMobile(mobileCountry, request.getMobile().trim())
                        != null) {
            throw new BizException(ErrorCode.REGISTER_USER_ALREADY_EXISTS);
        }

        // 领取全局内部用户 ID（meta）
        long userId = fetchUserId();

        // 分片决策（可插拔）
        int shardIndex = shardAllocator.allocate(userId);

        // 编码外部 ID（最新版本密钥）
        String externalId = externalIdGenerator.encode(userId, shardIndex);

        // 同一本地事务写入 auth_user + 注册 outbox
        AuthUser user = buildUser(request, username, email, mobileCountry, userId, shardIndex, externalId);
        // Preflight/ID/encoding failures preserve the code. Consume before any account is persisted,
        // so concurrent usernames cannot reuse a single challenge. A DB failure requires a new code.
        if (!emailCodeService.verify(email, EmailCodePurpose.REGISTER, request.getEmailCode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱验证码错误或已过期，请重新获取");
        }
        try {
            registerPersistenceService.persistWithOutbox(user);
        } catch (DuplicateKeyException error) {
            throw new BizException(ErrorCode.REGISTER_USER_ALREADY_EXISTS);
        }
        // The proxied persistence call has committed; the default role and profile are idempotent.
        if (registrationSync != null) registrationSync.synchronizeRegistered(user);

        log.info("注册成功 userId={} shardIndex={} 外部ID与 outbox 已落库", userId, shardIndex);
        return externalId;
    }

    /** 构造待落库的用户实体（密码 BCrypt，时间取时钟）。 */
    private AuthUser buildUser(
            RegisterRequest request,
            String username,
            String email,
            String mobileCountry,
            long userId,
            int shardIndex,
            String externalId) {
        AuthUser user = new AuthUser();
        user.setUserId(userId);
        user.setShardIndex(shardIndex);
        user.setExternalId(externalId);
        user.setUsername(username);
        user.setEmail(email);
        user.setMobileCountry(mobileCountry);
        user.setMobile(blankToNull(request.getMobile()));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setFailCount(0);
        user.setPasswordUpdateTime(now());
        user.setCreateTime(now());
        user.setUpdateTime(now());
        return user;
    }

    /** 领取全局内部用户 ID。 */
    private long fetchUserId() {
        try {
            Result<Long> result = metaIdClient.nextId("user");
            if (result == null || result.getCode() != 200 || result.getData() == null) {
                throw new BizException(ErrorCode.REGISTER_FAILED, "meta 取号失败");
            }
            return result.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 meta 取号失败", e);
            throw new BizException(ErrorCode.REGISTER_FAILED, "取号服务不可用");
        }
    }

    /** 解析手机号国家号：未填时手机号存在则默认 +86，否则 null。 */
    private String resolveMobileCountry(RegisterRequest request) {
        if (request.getMobile() != null && !request.getMobile().isBlank()) {
            return (request.getMobileCountry() == null || request.getMobileCountry().isBlank())
                    ? "+86"
                    : request.getMobileCountry().trim();
        }
        return blankToNull(request.getMobileCountry());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
