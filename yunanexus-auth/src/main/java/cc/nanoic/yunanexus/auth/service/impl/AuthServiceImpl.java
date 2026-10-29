package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.client.UserProfileSyncClient;
import cc.nanoic.yunanexus.auth.client.UserProfileView;
import cc.nanoic.yunanexus.auth.domain.LoginRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.MeResponse;
import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.entity.AuthLoginAttempt;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthLoginAttemptMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import cc.nanoic.yunanexus.auth.service.AuthService;
import cc.nanoic.yunanexus.auth.service.AuthenticationService;
import cc.nanoic.yunanexus.auth.service.CaptchaService;
import cc.nanoic.yunanexus.auth.service.LoginAttemptService;
import cc.nanoic.yunanexus.auth.service.SessionService;
import cc.nanoic.yunanexus.auth.service.TokenBlacklistService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 认证服务实现（登录 / 刷新 / 登出编排）。<p>
 *
 * 登录流程：<br>
 * 1. 校验客户端存在且启用；<br>
 * 2. 按凭证（username / email / mobile）检索用户；<br>
 * 3. 状态检查（禁用 / 锁定 / 需验证码）；<br>
 * 4. 密码校验前捕获 epoch，签发前后复核；<br>
 * 5. 失败计数原子自增 + 达到阈值锁定；<br>
 * 6. 平台授权校验；<br>
 * 7. 签发 access + refresh，建立会话（绑定 epoch）；<br>
 * 8. 异步记录登录尝试日志。<p>
 *
 * 安全约定：密码、令牌、epoch 一律不落日志。
 *
 * @author yunanexus
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final cc.nanoic.yunanexus.auth.service.OAuthApplicationService oauthApplications;

    private final AuthUserMapper authUserMapper;
    private final AuthClientMapper authClientMapper;
    private final AuthPlatformMapper authPlatformMapper;
    private final AuthLoginAttemptMapper loginAttemptMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final cc.nanoic.yunanexus.auth.service.impl.LoginGuardService loginGuardService;
    private final CaptchaService captchaService;
    private final TokenService tokenService;
    private final TokenBlacklistService blacklistService;
    private final SessionService sessionService;
    private final AuthProperties properties;
    private final Clock clock;
    private final AccountEpochService accountEpochService;
    private final AuthenticationService authenticationService;
    private final UserProfileSyncClient userProfileSyncClient;

    public AuthServiceImpl(AuthUserMapper authUserMapper,
                           AuthClientMapper authClientMapper,
                           AuthPlatformMapper authPlatformMapper,
                           AuthLoginAttemptMapper loginAttemptMapper,
                           PasswordEncoder passwordEncoder,
                           LoginAttemptService loginAttemptService,
                           cc.nanoic.yunanexus.auth.service.impl.LoginGuardService loginGuardService,
                           CaptchaService captchaService,
                           TokenService tokenService,
                           TokenBlacklistService blacklistService,
                           SessionService sessionService,
                           AuthProperties properties,
                           Clock clock,
                           AccountEpochService accountEpochService,
                           AuthenticationService authenticationService,
                           UserProfileSyncClient userProfileSyncClient,
                           cc.nanoic.yunanexus.auth.service.OAuthApplicationService oauthApplications) {
        this.oauthApplications=oauthApplications;
        this.authUserMapper = authUserMapper;
        this.authClientMapper = authClientMapper;
        this.authPlatformMapper = authPlatformMapper;
        this.loginAttemptMapper = loginAttemptMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.loginGuardService = loginGuardService;
        this.captchaService = captchaService;
        this.tokenService = tokenService;
        this.blacklistService = blacklistService;
        this.sessionService = sessionService;
        this.properties = properties;
        this.clock = clock;
        this.accountEpochService = accountEpochService;
        this.authenticationService = authenticationService;
        this.userProfileSyncClient = userProfileSyncClient;
    }

    @Override
    public LoginResult login(LoginRequest request, String ip) {
        AuthClient client = requireEnabledClient(request.getClientCode());
        if(!cc.nanoic.yunanexus.auth.entity.ApplicationCategory.allowsPassword(client.getApplicationCategory()))
            throw new BizException(ErrorCode.FORBIDDEN,"此应用仅允许授权码登录");
        if(client.getClientCode().startsWith("oauth_"))
            oauthApplications.verifySecret(client.getClientCode(),request.getClientSecret());

        AuthUser user = findByCredential(request.getUsername());
        if (user == null) {
            recordAttempt(request.getUsername(), null, null, false, "USER_NOT_FOUND", ip);
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        checkStatus(user);

        if (loginAttemptService.captchaRequired(user.getUsername())) {
            if (request.getCaptchaId() == null || request.getCaptchaId().isBlank()) {
                recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(),
                        false, "CAPTCHA_REQUIRED", ip);
                throw new BizException(ErrorCode.CAPTCHA_REQUIRED);
            }
            if (!captchaService.verify(request.getCaptchaId(), request.getCaptcha())) {
                recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(),
                        false, "CAPTCHA_ERROR", ip);
                throw new BizException(ErrorCode.CAPTCHA_ERROR);
            }
        }

        // 密码校验前捕获 epoch
        String epoch = accountEpochService.currentOrCreate(user.getUserId());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int failCount = loginAttemptService.recordFailure(user.getUsername());
            authUserMapper.incrementFailCount(user.getUsername(), now());
            // 分级处置：3 次出验证码 / 5 次冻结并通知 / 10 次冻结并告警管理员
            var decision = loginGuardService.onFailure(user.getUsername(), ip);
            if (decision.tier() == cc.nanoic.yunanexus.auth.service.impl.LoginGuardService.Tier.LOCK_ALERT_ADMIN) {
                log.warn("LOGIN_ALERT_ADMIN username={} ip={} failures={}", user.getUsername(), ip,
                        decision.userFailures());
                recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(),
                        false, "LOGIN_ALERT_ADMIN", ip);
                lockAccount(user, decision.userFailures());
                throw new BizException(ErrorCode.USER_LOCKED);
            }
            if (decision.tier() == cc.nanoic.yunanexus.auth.service.impl.LoginGuardService.Tier.LOCK_NOTIFY) {
                log.warn("LOGIN_LOCK_NOTIFY username={} ip={} failures={} lock={}s", user.getUsername(), ip,
                        decision.userFailures(), decision.lockSeconds());
                recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(),
                        false, "LOGIN_LOCK_NOTIFY", ip);
                lockAccount(user, decision.userFailures());
                throw new BizException(ErrorCode.USER_LOCKED);
            }
            if (failCount >= properties.getLockThreshold()) {
                lockAccount(user, failCount);
                recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(),
                        false, "USER_LOCKED", ip);
                throw new BizException(ErrorCode.USER_LOCKED);
            }
            recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(),
                    false, "BAD_CREDENTIALS", ip);
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }

        AuthPlatform platform = authPlatformMapper.selectByUserIdAndClient(user.getUserId(), client.getClientCode());
        if (platform == null || platform.getStatus() == null || platform.getStatus() != 1) {
            recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(),
                    false, "PLATFORM_AUTH_REVOKED", ip);
            throw new BizException(ErrorCode.PLATFORM_AUTH_REVOKED);
        }

        // 签发前 epoch 复核
        if (!accountEpochService.isCurrent(user.getUserId(), epoch)) {
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }

        if (Integer.valueOf(1).equals(user.getPasswordResetRequired())) throw new BizException(ErrorCode.BAD_REQUEST,"请先通过邮箱重置流程设置新密码");
        loginAttemptService.reset(user.getUsername());
        authUserMapper.resetFailCountOnLoginSuccess(user.getUsername(), now(), ip);

        LoginResult result = issueTokensAndSession(user, client.getClientCode(), ip, epoch);

        recordAttempt(user.getUsername(), user.getUserId(), request.getClientCode(), true, null, ip);
        log.info("登录成功 userId={} client={}", user.getUserId(), client.getClientCode());
        return result;
    }

    @Override
    public LoginResult refresh(String refreshToken) {
        // 先验签，保留输入令牌原 JTI
        TokenService.TokenPayload original = tokenService.verifyRefreshToken(refreshToken);

        AuthenticatedIdentity identity = authenticationService.authenticateRefresh(refreshToken);
        SessionInfo session = authenticationService.requireSession(identity.sid());

        // 验证输入令牌原 JTI 与会话一致
        if (!Objects.equals(original.jti(), session.getRefreshJti())) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        // 验证输入令牌其他声明与会话一致
        if (!Objects.equals(original.externalId(), session.getExternalId())
                || !Objects.equals(original.clientCode(), session.getClientCode())
                || !Objects.equals(original.sid(), session.getSid())) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 保留旧 epoch
        String epoch = session.getAccountEpoch();
        if (epoch == null || epoch.length() != 22) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // requireGrant 再次校验
        authenticationService.requireGrant(session, session.getClientCode());

        // 签发前检查 current
        if (!accountEpochService.isCurrent(session.getUserId(), epoch)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String newAccessJti = UUID.randomUUID().toString().replace("-", "");
        String newRefreshJti = UUID.randomUUID().toString().replace("-", "");
        TokenService.TokenPair pair = tokenService.issueTokenPair(session.getExternalId(),
                session.getClientCode(), session.getSid(), newAccessJti, newRefreshJti);

        long loginTimeMillis = session.getLoginTimeMillis();
        long accessExpiresAtMillis = pair.accessExpiresAt().toEpochMilli();
        long refreshExpiresAtMillis = pair.refreshExpiresAt().toEpochMilli();
        long sessionTtlMillis = Math.multiplyExact(properties.getSessionTtlSeconds(), 1000L);
        long expireAt = Math.min(refreshExpiresAtMillis, Math.addExact(clock.millis(), sessionTtlMillis));
        SessionInfo updated = new SessionInfo(session.getSid(), session.getUserId(),
                session.getExternalId(), session.getUsername(), session.getClientCode(),
                session.getDeviceId(), newAccessJti, newRefreshJti, loginTimeMillis, expireAt,
                accessExpiresAtMillis, refreshExpiresAtMillis, epoch);

        // CAS 使用输入令牌原 JTI
        boolean rotated = sessionService.rotate(session.getSid(), original.jti(), updated);
        if (!rotated) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 轮换后撤销旧 access 令牌：刷新语义要求"旧的两个令牌同时失效"，
        // 仅靠会话 CAS 只能让旧 refresh 失效，旧 access 仍可在 TTL 内继续使用。
        blacklistService.revoke(session.getSid(), session.getAccessJti(),
                java.time.Instant.ofEpochMilli(session.getAccessExpiresAtMillis()));

        // CAS 后检查 current，Redis 故障必须抛出（不吞）
        if (!accountEpochService.isCurrent(session.getUserId(), epoch)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        return new LoginResult(pair.accessToken(), pair.refreshToken(), properties.getAccessTokenTtlSeconds(), session.getExternalId());
    }

    @Override
    public void logout(String accessToken) {
        AuthenticatedIdentity identity;
        try {
            identity = authenticationService.authenticateAccess(accessToken);
        } catch (BizException e) {
            // 保持幂等：SESSION_NOT_FOUND 直接返回成功
            if (e.getCode() == ErrorCode.SESSION_NOT_FOUND.getCode()) {
                return;
            }
            throw e;
        }

        SessionInfo session;
        try {
            session = authenticationService.requireSession(identity.sid());
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.SESSION_NOT_FOUND.getCode()) {
                return;
            }
            throw e;
        }

        if (!Objects.equals(identity.userId(), session.getUserId())
                || !Objects.equals(identity.externalId(), session.getExternalId())
                || !Objects.equals(identity.clientCode(), session.getClientCode())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        sessionService.remove(session.getSid());
        log.info("登出成功 userId={}", session.getUserId());
    }

    @Override
    public void logoutAll(AuthenticatedIdentity identity) {
        SessionInfo session = authenticationService.requireSession(identity.sid());
        if (session.getUserId() != identity.userId()
                || !Objects.equals(session.getExternalId(), identity.externalId())
                || !Objects.equals(session.getClientCode(), identity.clientCode())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        accountEpochService.revoke(session.getUserId());
        log.info("全局登出成功 userId={}", session.getUserId());
    }

    @Override
    public MeResponse me(AuthenticatedIdentity identity) {
        SessionInfo session = authenticationService.requireSession(identity.sid());
        if (session.getUserId() != identity.userId()
                || !Objects.equals(session.getExternalId(), identity.externalId())
                || !Objects.equals(session.getClientCode(), identity.clientCode())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        UserProfileView profile = loadProfileQuietly(session.getUserId());
        return new MeResponse(session.getExternalId(), session.getClientCode(),
                profile == null ? null : profile.getUsername(),
                profile == null ? null : profile.getNickname(),
                profile == null ? null : profile.getAvatar());
    }

    /**
     * 聚合用户中心资料；用户中心不可用时降级为 {@code null}，不影响身份获取。
     */
    private UserProfileView loadProfileQuietly(long userId) {
        try {
            return userProfileSyncClient.getProfile(userId);
        } catch (Exception e) {
            log.warn("聚合用户资料失败，降级返回 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    public LoginResult issueForSession(SessionInfo source, String targetClient) {
        if (source == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (targetClient == null || targetClient.isBlank() || targetClient.length() > 64) {
            throw new BizException(ErrorCode.CLIENT_NOT_FOUND);
        }

        SessionInfo session = authenticationService.requireSession(source.getSid());
        if (session.getUserId() != source.getUserId()
                || !Objects.equals(session.getExternalId(), source.getExternalId())
                || !Objects.equals(session.getAccountEpoch(), source.getAccountEpoch())) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }

        authenticationService.requireGrant(session, targetClient);

        String epoch = session.getAccountEpoch();
        if (!accountEpochService.isCurrent(session.getUserId(), epoch)) {
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }

        String newSid = UUID.randomUUID().toString().replace("-", "");
        String accessJti = UUID.randomUUID().toString().replace("-", "");
        String refreshJti = UUID.randomUUID().toString().replace("-", "");
        TokenService.TokenPair pair = tokenService.issueTokenPair(session.getExternalId(), targetClient,
                newSid, accessJti, refreshJti);

        long loginTime = clock.millis();
        long accessExpiresAtMillis = pair.accessExpiresAt().toEpochMilli();
        long refreshExpiresAtMillis = pair.refreshExpiresAt().toEpochMilli();
        long sessionTtlMillis = Math.multiplyExact(properties.getSessionTtlSeconds(), 1000L);
        long expireAt = Math.min(refreshExpiresAtMillis, Math.addExact(loginTime, sessionTtlMillis));
        SessionInfo child = new SessionInfo(newSid, session.getUserId(), session.getExternalId(),
                session.getUsername(), targetClient, session.getDeviceId(), accessJti, refreshJti,
                loginTime, expireAt, accessExpiresAtMillis, refreshExpiresAtMillis, epoch);
        sessionService.create(child);

        // 创建后 epoch check
        if (!accountEpochService.isCurrent(session.getUserId(), epoch)) {
            sessionService.remove(newSid);
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }

        return new LoginResult(pair.accessToken(), pair.refreshToken(), properties.getAccessTokenTtlSeconds(), session.getExternalId());
    }

    private AuthUser findByCredential(String credential) {
        AuthUser user = authUserMapper.selectByUsername(credential);
        if (user != null) {
            return user;
        }
        user = authUserMapper.selectByEmail(credential);
        if (user != null) {
            return user;
        }
        // 手机号作为登录凭证默认关闭（无 SMS 能力无法校验归属）；关闭时视同凭证不存在，避免信息泄露
        if (!properties.isMobileEnabled()) {
            return null;
        }
        return authUserMapper.selectByMobile("+86", credential);
    }

    private AuthClient requireEnabledClient(String clientCode) {
        AuthClient client = authClientMapper.selectByClientCode(clientCode);
        if (client == null) {
            throw new BizException(ErrorCode.CLIENT_NOT_FOUND);
        }
        if (client.getStatus() == null || client.getStatus() != 1) {
            throw new BizException(ErrorCode.CLIENT_DISABLED);
        }
        return client;
    }

    private void checkStatus(AuthUser user) {
        if (user.getStatus() == null) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        if (user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        if (user.getStatus() == 2) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(now())) {
                authUserMapper.updateStatusAndLock(user.getUserId(), 1, null, null);
                loginAttemptService.reset(user.getUsername());
                log.info("账号锁定已到期，自动解锁 userId={}", user.getUserId());
            } else {
                throw new BizException(ErrorCode.USER_LOCKED);
            }
        } else if (user.getStatus() != 1) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        if (loginAttemptService.isLocked(user.getUsername())) {
            throw new BizException(ErrorCode.USER_LOCKED);
        }
    }

    private void lockAccount(AuthUser user, int failCount) {
        long lockedUntil = loginAttemptService.lock(user.getUsername());
        authUserMapper.updateStatusAndLock(user.getUserId(), 2,
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(lockedUntil), clock.getZone()),
                "连续登录失败 " + failCount + " 次");
        log.warn("账号锁定 userId={} 连续失败={}", user.getUserId(), failCount);
    }

    private LoginResult issueTokensAndSession(AuthUser user, String clientCode, String ip, String epoch) {
        // helper 内部签发前复验
        if (!accountEpochService.isCurrent(user.getUserId(), epoch)) {
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }

        String accessJti = UUID.randomUUID().toString().replace("-", "");
        String refreshJti = UUID.randomUUID().toString().replace("-", "");
        String sid = UUID.randomUUID().toString().replace("-", "");

        TokenService.TokenPair pair = tokenService.issueTokenPair(user.getExternalId(), clientCode, sid, accessJti, refreshJti);

        long loginTime = clock.millis();
        long accessExpiresAtMillis = pair.accessExpiresAt().toEpochMilli();
        long refreshExpiresAtMillis = pair.refreshExpiresAt().toEpochMilli();
        long sessionTtlMillis = Math.multiplyExact(properties.getSessionTtlSeconds(), 1000L);
        long expireAt = Math.min(refreshExpiresAtMillis, Math.addExact(loginTime, sessionTtlMillis));
        SessionInfo session = new SessionInfo(sid, user.getUserId(), user.getExternalId(),
                user.getUsername(), clientCode, null, accessJti, refreshJti, loginTime, expireAt,
                accessExpiresAtMillis, refreshExpiresAtMillis, epoch);
        sessionService.create(session);

        // 创建后最终检查：epoch 变化则删除新会话并拒绝
        if (!accountEpochService.isCurrent(user.getUserId(), epoch)) {
            try {
                sessionService.remove(sid);
            } catch (Exception e) {
                log.warn("清理新会话失败 sid={} userId={}", sid, user.getUserId(), e);
            }
            throw new BizException(ErrorCode.TOKEN_REVOKED);
        }

        return new LoginResult(pair.accessToken(), pair.refreshToken(), properties.getAccessTokenTtlSeconds(), user.getExternalId());
    }

    private void recordAttempt(String username, Long userId, String clientCode, boolean success,
                               String failReason, String ip) {
        try {
            AuthLoginAttempt attempt = new AuthLoginAttempt();
            attempt.setUserId(userId);
            attempt.setUsername(username);
            attempt.setLoginType("password");
            attempt.setSuccess(success);
            attempt.setFailReason(failReason);
            attempt.setIp(ip);
            attempt.setCreateTime(now());
            loginAttemptMapper.insert(attempt);
        } catch (Exception e) {
            log.warn("登录尝试记录写入失败 username={}", username, e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
