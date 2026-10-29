package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.client.UserProfileSyncClient;
import cc.nanoic.yunanexus.auth.domain.LoginRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.MeResponse;
import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthLoginAttemptMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import cc.nanoic.yunanexus.auth.service.AuthenticationService;
import cc.nanoic.yunanexus.auth.service.CaptchaService;
import cc.nanoic.yunanexus.auth.service.LoginAttemptService;
import cc.nanoic.yunanexus.auth.service.SessionService;
import cc.nanoic.yunanexus.auth.service.TokenBlacklistService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AuthServiceImpl} 单元测试。<p>
 *
 * 覆盖：登录成功、账号不存在、密码错误、账号禁用、账号锁定、需验证码、验证码错误、
 * 平台授权撤销、refresh 成功、refresh 各种失配、logout 成功与失配。
 * 所有外部依赖（Mapper / Redis 服务 / 令牌服务）均 mock。
 * 新增：epoch 竞态测试、真实 DefaultAuthenticationService、logoutAll 等。
 *
 * @author yunanexus
 */
class AuthServiceImplTest {

    private static final String FIXED_EPOCH = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER_EPOCH = "BBBBBBBBBBBBBBBBBBBBBB";

    private AuthUserMapper authUserMapper;
    private AuthClientMapper authClientMapper;
    private AuthPlatformMapper authPlatformMapper;
    private AuthLoginAttemptMapper loginAttemptMapper;
    private PasswordEncoder passwordEncoder;
    private LoginAttemptService loginAttemptService;
    private CaptchaService captchaService;
    private TokenService tokenService;
    private TokenBlacklistService blacklistService;
    private SessionService sessionService;
    private AccountEpochService accountEpochService;
    private AuthenticationService authenticationService;
    private UserProfileSyncClient userProfileSyncClient;
    private AuthProperties properties;
    private AuthServiceImpl service;

    private AuthUser user;
    private AuthClient client;
    private AuthPlatform platform;
    private Clock clock;
    private Instant now;

    @BeforeEach
    void setUp() {
        authUserMapper = mock(AuthUserMapper.class);
        authClientMapper = mock(AuthClientMapper.class);
        authPlatformMapper = mock(AuthPlatformMapper.class);
        loginAttemptMapper = mock(AuthLoginAttemptMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        loginAttemptService = mock(LoginAttemptService.class);
        captchaService = mock(CaptchaService.class);
        tokenService = mock(TokenService.class);
        blacklistService = mock(TokenBlacklistService.class);
        sessionService = mock(SessionService.class);
        accountEpochService = mock(AccountEpochService.class);
        userProfileSyncClient = mock(UserProfileSyncClient.class);

        properties = new AuthProperties();
        properties.setLockThreshold(7);
        properties.setCaptchaThreshold(3);
        properties.setAccessTokenTtlSeconds(1800);
        properties.setRefreshTokenTtlSeconds(604800);
        properties.setSessionTtlSeconds(604800);

        now = Instant.parse("2026-01-01T00:00:00Z");
        clock = Clock.fixed(now, ZoneOffset.UTC);

        authenticationService = new DefaultAuthenticationService(
                tokenService, sessionService, accountEpochService,
                authUserMapper, authClientMapper, authPlatformMapper, clock);

        service = new AuthServiceImpl(authUserMapper, authClientMapper, authPlatformMapper,
                loginAttemptMapper, passwordEncoder, loginAttemptService, captchaService,
                tokenService, blacklistService, sessionService, properties, clock,
                accountEpochService, authenticationService, userProfileSyncClient, mock(cc.nanoic.yunanexus.auth.service.OAuthApplicationService.class));

        client = new AuthClient();
        client.setApplicationCategory("OFFICIAL_NATIVE");
        client.setClientCode("web");
        client.setStatus(1);

        platform = new AuthPlatform();
        platform.setUserId(10001L);
        platform.setClientCode("web");
        platform.setStatus(1);

        user = new AuthUser();
        user.setUserId(10001L);
        user.setUsername("alice");
        user.setExternalId("ext-alice");
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setStatus(1);
        user.setFailCount(0);

        // 默认 epoch mock
        when(accountEpochService.currentOrCreate(anyLong())).thenReturn(FIXED_EPOCH);
        when(accountEpochService.isCurrent(anyLong(), eq(FIXED_EPOCH))).thenReturn(true);

        // 默认 client / platform grant / user 有效
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, "web")).thenReturn(platform);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(loginAttemptService.captchaRequired("alice")).thenReturn(false);
        when(loginAttemptService.isLocked("alice")).thenReturn(false);
    }

    private LoginRequest loginRequest() {
        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("secret123");
        req.setClientCode("web");
        return req;
    }

    private SessionInfo buildSession(String sid, String externalId, String clientCode,
                                     String accessJti, String refreshJti, long expireAtMillis,
                                     String epoch) {
        long nowMillis = now.toEpochMilli();
        return new SessionInfo(sid, 10001L, externalId, "alice", clientCode,
                null, accessJti, refreshJti, nowMillis, expireAtMillis,
                nowMillis + 1800000L, expireAtMillis, epoch);
    }

    private SessionInfo buildSession(String sid, String externalId, String clientCode,
                                     String accessJti, String refreshJti, long expireAtMillis) {
        return buildSession(sid, externalId, clientCode, accessJti, refreshJti,
                expireAtMillis, FIXED_EPOCH);
    }

    private long futureExpire() {
        return now.toEpochMilli() + 604800000L;
    }

    private long pastExpire() {
        return now.toEpochMilli() - 1000L;
    }

    private void mockValidDefaultGrant() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, "web")).thenReturn(platform);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH)).thenReturn(true);
    }

    private TokenService.TokenPayload refreshPayload(String jti, String sid, Instant expiresAt) {
        return new TokenService.TokenPayload(jti, "ext-alice", "web", sid, expiresAt);
    }

    private TokenService.TokenPayload accessPayload(String jti, String sid, Instant expiresAt) {
        return new TokenService.TokenPayload(jti, "ext-alice", "web", sid, expiresAt);
    }

    @Test
    void 登录成功返回令牌与外部ID() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(loginAttemptService.captchaRequired("alice")).thenReturn(false);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, "web"))
                .thenReturn(platform);
        when(accountEpochService.currentOrCreate(10001L)).thenReturn(FIXED_EPOCH);
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH)).thenReturn(true);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), anyString(), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("access-token", now.plusSeconds(1800),
                        "refresh-token", now.plusSeconds(604800)));

        LoginResult result = service.login(loginRequest(), "127.0.0.1");

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        assertEquals("ext-alice", result.getExternalId());
        verify(authUserMapper).resetFailCountOnLoginSuccess(eq("alice"), any(), eq("127.0.0.1"));

        // 捕获 SessionInfo 验证 epoch
        ArgumentCaptor<SessionInfo> sessionCaptor = ArgumentCaptor.forClass(SessionInfo.class);
        verify(sessionService).create(sessionCaptor.capture());
        SessionInfo captured = sessionCaptor.getValue();
        assertEquals(FIXED_EPOCH, captured.getAccountEpoch());
        assertEquals(10001L, captured.getUserId());
        assertEquals("ext-alice", captured.getExternalId());

        verify(tokenService, never()).issueAccessToken(anyString(), anyString(), anyString(), anyString());
        verify(tokenService, never()).issueRefreshToken(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 账号不存在应拒绝() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByUsername("alice")).thenReturn(null);
        when(authUserMapper.selectByEmail("alice")).thenReturn(null);
        when(authUserMapper.selectByMobile("+86", "alice")).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.login(loginRequest(), "127.0.0.1"));
        assertEquals(30001, e.getCode());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 手机号凭证在开关关闭时应视同不存在() {
        properties.setMobileEnabled(false);
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByMobile("+86", "13800000000")).thenReturn(user);

        LoginRequest req = loginRequest();
        req.setUsername("13800000000");
        BizException e = assertThrows(BizException.class, () -> service.login(req, "127.0.0.1"));
        assertEquals(ErrorCode.BAD_CREDENTIALS.getCode(), e.getCode());
        verify(authUserMapper, never()).selectByMobile(anyString(), anyString());
    }

    @Test
    void 手机号凭证在开关开启时可登录() {
        properties.setMobileEnabled(true);
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByMobile("+86", "13800000000")).thenReturn(user);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), anyString(), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("access-token", now.plusSeconds(1800),
                        "refresh-token", now.plusSeconds(604800)));

        LoginRequest req = loginRequest();
        req.setUsername("13800000000");
        LoginResult result = service.login(req, "127.0.0.1");

        assertNotNull(result);
        assertEquals("access-token", result.getAccessToken());
        verify(authUserMapper).selectByMobile("+86", "13800000000");
    }

    @Test
    void 密码错误应拒绝并记录失败() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(loginAttemptService.captchaRequired("alice")).thenReturn(false);
        when(loginAttemptService.recordFailure("alice")).thenReturn(1);
        when(accountEpochService.currentOrCreate(10001L)).thenReturn(FIXED_EPOCH);

        LoginRequest req = loginRequest();
        req.setPassword("wrong-password");
        BizException e = assertThrows(BizException.class, () -> service.login(req, "127.0.0.1"));
        assertEquals(30001, e.getCode());
        verify(loginAttemptService).recordFailure("alice");
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 账号禁用应拒绝() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        user.setStatus(0);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);

        BizException e = assertThrows(BizException.class, () -> service.login(loginRequest(), "127.0.0.1"));
        assertEquals(30002, e.getCode());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 账号锁定应拒绝() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        user.setStatus(2);
        user.setLockedUntil(java.time.LocalDateTime.of(2099, 1, 1, 0, 0));
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);

        BizException e = assertThrows(BizException.class, () -> service.login(loginRequest(), "127.0.0.1"));
        assertEquals(30003, e.getCode());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 需验证码但未携带应拒绝() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(loginAttemptService.captchaRequired("alice")).thenReturn(true);

        BizException e = assertThrows(BizException.class, () -> service.login(loginRequest(), "127.0.0.1"));
        assertEquals(30004, e.getCode());
        verify(captchaService, never()).verify(anyString(), anyString());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 验证码错误应拒绝() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(loginAttemptService.captchaRequired("alice")).thenReturn(true);
        when(captchaService.verify("cap-1", "000000")).thenReturn(false);

        LoginRequest req = loginRequest();
        req.setCaptchaId("cap-1");
        req.setCaptcha("000000");
        BizException e = assertThrows(BizException.class, () -> service.login(req, "127.0.0.1"));
        assertEquals(30005, e.getCode());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void 平台授权撤销应拒绝() {
        when(authClientMapper.selectByClientCode("web")).thenReturn(client);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(loginAttemptService.captchaRequired("alice")).thenReturn(false);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, "web")).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.login(loginRequest(), "127.0.0.1"));
        assertEquals(30009, e.getCode());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void refresh成功验证会话并续期() {
        String sid = "sid-1";
        String oldRefreshJti = "refresh-jti-1";
        String accessJti = "access-jti-1";
        long expireAt = futureExpire();
        Instant refreshExp = now.plusSeconds(604800);

        TokenService.TokenPayload payload = refreshPayload(oldRefreshJti, sid, refreshExp);
        SessionInfo session = buildSession(sid, "ext-alice", "web", accessJti, oldRefreshJti, expireAt);

        TokenService.TokenPair pair = new TokenService.TokenPair(
                "new-access-token", now.plusSeconds(1800), "new-refresh-token", refreshExp);

        mockValidDefaultGrant();
        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), eq(sid), anyString(), anyString()))
                .thenReturn(pair);
        when(sessionService.rotate(eq(sid), eq(oldRefreshJti), any(SessionInfo.class))).thenReturn(true);

        LoginResult result = service.refresh("refresh-token");

        assertNotNull(result);
        assertEquals("new-access-token", result.getAccessToken());
        assertEquals("ext-alice", result.getExternalId());
        verify(sessionService).rotate(eq(sid), eq(oldRefreshJti), any(SessionInfo.class));
        // 轮换后旧 access 必须被撤销：否则旧访问令牌在 TTL 内仍可用
        verify(blacklistService).revoke(eq(sid), anyString(), any(Instant.class));
        verify(sessionService, never()).remove(anyString());
    }

    @Test
    void refreshMissingSession应拒绝() {
        String sid = "sid-missing";
        String refreshJti = "refresh-jti-missing";
        TokenService.TokenPayload payload = refreshPayload(refreshJti, sid, now.plusSeconds(604800));

        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.refresh("refresh-token"));
        assertEquals(30201, e.getCode());
        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).rotate(anyString(), anyString(), any(SessionInfo.class));
        verify(sessionService, never()).remove(anyString());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    static Stream<SessionInfo> sessionBindingMismatches() {
        long now = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        long future = now + 604800000L;
        List<SessionInfo> mismatches = new ArrayList<>();
        // external ID mismatch
        mismatches.add(new SessionInfo("sid-1", 10001L, "ext-other", "alice", "web",
                null, "access-jti", "refresh-jti", now, future,
                now + 1800000L, future, FIXED_EPOCH));
        // client code mismatch
        mismatches.add(new SessionInfo("sid-1", 10001L, "ext-alice", "alice", "other-client",
                null, "access-jti", "refresh-jti", now, future,
                now + 1800000L, future, FIXED_EPOCH));
        // sid mismatch
        mismatches.add(new SessionInfo("sid-other", 10001L, "ext-alice", "alice", "web",
                null, "access-jti", "refresh-jti", now, future,
                now + 1800000L, future, FIXED_EPOCH));
        // refresh JTI mismatch
        mismatches.add(new SessionInfo("sid-1", 10001L, "ext-alice", "alice", "web",
                null, "access-jti", "other-refresh-jti", now, future,
                now + 1800000L, future, FIXED_EPOCH));
        return mismatches.stream();
    }

    @ParameterizedTest
    @MethodSource("sessionBindingMismatches")
    void refreshSessionBinding失配应拒绝(SessionInfo session) {
        // 固定输入令牌，让每个用例仅改变会话的一个绑定字段。
        TokenService.TokenPayload payload = new TokenService.TokenPayload(
                "refresh-jti", "ext-alice", "web", "sid-1", now.plusSeconds(604800));
        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get("sid-1")).thenReturn(session);
        // 另一客户端本身可用，避免在验证绑定前被“客户端不存在”分支短路。
        when(authClientMapper.selectByClientCode(session.getClientCode())).thenReturn(client);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, session.getClientCode())).thenReturn(platform);

        BizException e = assertThrows(BizException.class, () -> service.refresh("refresh-token"));
        ErrorCode expected = "other-refresh-jti".equals(session.getRefreshJti())
                ? ErrorCode.REFRESH_TOKEN_INVALID : ErrorCode.TOKEN_INVALID;
        assertEquals(expected.getCode(), e.getCode());
        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).rotate(anyString(), anyString(), any(SessionInfo.class));
        verify(sessionService, never()).remove(anyString());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void refreshSession过期应拒绝() {
        String sid = "sid-expired";
        String refreshJti = "refresh-jti-expired";
        TokenService.TokenPayload payload = refreshPayload(refreshJti, sid, now.plusSeconds(604800));
        SessionInfo session = buildSession(sid, "ext-alice", "web", "access-jti", refreshJti, pastExpire());

        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);

        BizException e = assertThrows(BizException.class, () -> service.refresh("refresh-token"));
        assertEquals(30101, e.getCode());
        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).rotate(anyString(), anyString(), any(SessionInfo.class));
        verify(sessionService, never()).remove(anyString());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void refresh用户ID不一致应拒绝() {
        String sid = "sid-user-mismatch";
        String refreshJti = "refresh-jti-user-mismatch";
        TokenService.TokenPayload payload = refreshPayload(refreshJti, sid, now.plusSeconds(604800));
        SessionInfo session = buildSession(sid, "ext-alice", "web", "access-jti", refreshJti, futureExpire());

        AuthUser mismatchedUser = new AuthUser();
        mismatchedUser.setUserId(99999L);
        mismatchedUser.setUsername("alice");
        mismatchedUser.setExternalId("ext-alice");
        mismatchedUser.setStatus(1);

        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);
        when(authUserMapper.selectByUsername("alice")).thenReturn(mismatchedUser);

        BizException e = assertThrows(BizException.class, () -> service.refresh("refresh-token"));
        assertEquals(30101, e.getCode());
        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).rotate(anyString(), anyString(), any(SessionInfo.class));
        verify(sessionService, never()).remove(anyString());
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void logout成功吊销并删除会话() {
        String sid = "sid-logout";
        String accessJti = "access-jti-logout";
        String refreshJti = "refresh-jti-logout";
        TokenService.TokenPayload payload = accessPayload(accessJti, sid, now.plusSeconds(1800));
        SessionInfo session = buildSession(sid, "ext-alice", "web", accessJti, refreshJti, futureExpire());

        mockValidDefaultGrant();
        when(tokenService.verifyAccessToken("access-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);

        service.logout("access-token");

        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService).remove(eq(sid));
    }

    @Test
    void logoutSessionBinding失配应拒绝() {
        String sid = "sid-logout-mismatch";
        String accessJti = "access-jti-logout-mismatch";
        String refreshJti = "refresh-jti-logout-mismatch";
        TokenService.TokenPayload payload = accessPayload(accessJti, sid, now.plusSeconds(1800));
        // 注意：这里故意让 session 的 accessJti 与 payload 不匹配，以触发 30101
        SessionInfo session = buildSession(sid, "ext-alice", "web", "other-access-jti", refreshJti, futureExpire());

        when(tokenService.verifyAccessToken("access-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);

        BizException e = assertThrows(BizException.class, () -> service.logout("access-token"));
        assertEquals(30101, e.getCode());
        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).remove(anyString());
    }

    @Test
    void logoutSession不存在幂等成功() {
        String sid = "sid-logout-missing";
        String accessJti = "access-jti-logout-missing";
        TokenService.TokenPayload payload = accessPayload(accessJti, sid, now.plusSeconds(1800));

        when(tokenService.verifyAccessToken("access-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(null);

        service.logout("access-token");

        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).remove(anyString());
    }

    @Test
    void refreshCAS失败返回30104且不返回令牌() {
        String sid = "sid-cas-fail";
        String oldRefreshJti = "refresh-jti-cas-fail";
        long expireAt = futureExpire();

        TokenService.TokenPayload payload = refreshPayload(oldRefreshJti, sid, now.plusSeconds(604800));
        SessionInfo session = buildSession(sid, "ext-alice", "web", "access-jti", oldRefreshJti, expireAt);

        mockValidDefaultGrant();
        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), eq(sid), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("new-access", now.plusSeconds(1800),
                        "new-refresh", now.plusSeconds(604800)));
        when(sessionService.rotate(eq(sid), eq(oldRefreshJti), any(SessionInfo.class))).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.refresh("refresh-token"));
        assertEquals(30104, e.getCode());
        verify(sessionService).rotate(eq(sid), eq(oldRefreshJti), any(SessionInfo.class));
        // CAS 失败：不签发、不撤销
        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).remove(anyString());
    }

    @Test
    void refresh成功SessionInfo精确exp元数据与pair一致() {
        String sid = "sid-pair-exp";
        String oldRefreshJti = "refresh-jti-pair-exp";
        long expireAt = futureExpire();
        Instant accessExp = now.plusSeconds(1800);
        Instant refreshExp = now.plusSeconds(604800);

        TokenService.TokenPayload payload = refreshPayload(oldRefreshJti, sid, refreshExp);
        SessionInfo session = buildSession(sid, "ext-alice", "web", "access-jti", oldRefreshJti, expireAt);

        TokenService.TokenPair pair = new TokenService.TokenPair(
                "new-access", accessExp, "new-refresh", refreshExp);

        mockValidDefaultGrant();
        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), eq(sid), anyString(), anyString()))
                .thenReturn(pair);
        when(sessionService.rotate(eq(sid), eq(oldRefreshJti), any(SessionInfo.class))).thenReturn(true);

        service.refresh("refresh-token");

        ArgumentCaptor<SessionInfo> captor = ArgumentCaptor.forClass(SessionInfo.class);
        verify(sessionService).rotate(eq(sid), eq(oldRefreshJti), captor.capture());
        SessionInfo captured = captor.getValue();
        assertEquals(accessExp.toEpochMilli(), captured.getAccessExpiresAtMillis());
        assertEquals(refreshExp.toEpochMilli(), captured.getRefreshExpiresAtMillis());
        assertEquals(FIXED_EPOCH, captured.getAccountEpoch());
    }

    // ---------------- 新增：epoch 竞态测试 ----------------

    @Test
    void 密码校验前捕获epoch而签发前epoch变更拒绝createNever() {
        when(accountEpochService.currentOrCreate(10001L)).thenReturn(FIXED_EPOCH);
        // 签发前 epoch 已变更
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH)).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.login(loginRequest(), "127.0.0.1"));
        assertEquals(30103, e.getCode());
        verify(sessionService, never()).create(any(SessionInfo.class));
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void create之后epoch变更删除OWNnewSid拒绝() {
        // 用实际创建事件触发撤销，避免依赖签发前复验的调用次数。
        AtomicBoolean created = new AtomicBoolean();
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH))
                .thenAnswer(invocation -> !created.get());
        doAnswer(invocation -> {
            created.set(true);
            return null;
        }).when(sessionService).create(any(SessionInfo.class));

        // 需要先捕获到 session 创建时使用的 sid
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), anyString(), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("access", now.plusSeconds(1800),
                        "refresh", now.plusSeconds(604800)));

        BizException e = assertThrows(BizException.class, () -> service.login(loginRequest(), "127.0.0.1"));
        assertEquals(30103, e.getCode());

        // 验证 session 创建过，并且随后被删除
        ArgumentCaptor<SessionInfo> sessionCaptor = ArgumentCaptor.forClass(SessionInfo.class);
        verify(sessionService).create(sessionCaptor.capture());
        String createdSid = sessionCaptor.getValue().getSid();
        verify(sessionService).remove(eq(createdSid));
    }

    @Test
    void refresh验证原JTI之后第二次读到别人更新的新JTI拒绝不调用CAS() {
        String sid = "sid-race-refresh";
        String originalJti = "original-refresh-jti";
        String newJtiFromOther = "other-new-refresh-jti";
        long expireAt = futureExpire();
        Instant refreshExp = now.plusSeconds(604800);

        TokenService.TokenPayload payload = refreshPayload(originalJti, sid, refreshExp);
        // 第一次 get 返回 original session（用于验证 JTI）
        SessionInfo originalSession = buildSession(sid, "ext-alice", "web", "access-jti", originalJti, expireAt);
        // 第二次 get 返回已被他人更新的 session（不同 refreshJti）
        SessionInfo rotatedSession = buildSession(sid, "ext-alice", "web", "new-access-jti", newJtiFromOther, expireAt);

        mockValidDefaultGrant();
        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(originalSession, rotatedSession);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), eq(sid), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("new-access", now.plusSeconds(1800),
                        "new-refresh", refreshExp));

        BizException e = assertThrows(BizException.class, () -> service.refresh("refresh-token"));
        assertEquals(30104, e.getCode());
        verify(sessionService, never()).rotate(anyString(), anyString(), any(SessionInfo.class));
        verify(blacklistService, never()).revoke(anyString(), anyString(), any(Instant.class));
        verify(sessionService, never()).remove(anyString());
    }

    @Test
    void refreshCAS后epoch变更拒绝无tokens() {
        String sid = "sid-cas-epoch-change";
        String originalJti = "refresh-jti-cas-epoch";
        long expireAt = futureExpire();
        Instant refreshExp = now.plusSeconds(604800);

        TokenService.TokenPayload payload = refreshPayload(originalJti, sid, refreshExp);
        SessionInfo session = buildSession(sid, "ext-alice", "web", "access-jti", originalJti, expireAt);

        // 默认 grant 有效
        mockValidDefaultGrant();
        // 在 requireSession / requireGrant 阶段 epoch 有效
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH)).thenReturn(true);
        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(payload);
        when(sessionService.get(sid)).thenReturn(session);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), eq(sid), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("new-access", now.plusSeconds(1800),
                        "new-refresh", refreshExp));
        when(sessionService.rotate(eq(sid), eq(originalJti), any(SessionInfo.class))).thenReturn(true);

        // CAS 确实成功后再撤销，确保测试覆盖 CAS 后的检查。
        AtomicBoolean rotated = new AtomicBoolean();
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH))
                .thenAnswer(invocation -> !rotated.get());
        when(sessionService.rotate(eq(sid), eq(originalJti), any(SessionInfo.class)))
                .thenAnswer(invocation -> {
                    rotated.set(true);
                    return true;
                });

        BizException e = assertThrows(BizException.class, () -> service.refresh("refresh-token"));
        assertEquals(30104, e.getCode());
        verify(sessionService).rotate(eq(sid), eq(originalJti), any(SessionInfo.class));
        verify(blacklistService).revoke(eq(sid), anyString(), any(Instant.class));
        verify(sessionService, never()).remove(anyString());
    }

    @Test
    void logoutAll只revoke当前uid不靠kickAll() {
        String sid = "sid-logout-all";
        AuthenticatedIdentity identity = new AuthenticatedIdentity("ext-alice", "web", sid, 10001L);
        SessionInfo session = buildSession(sid, "ext-alice", "web", "access-jti", "refresh-jti", futureExpire());

        mockValidDefaultGrant();
        when(sessionService.get(sid)).thenReturn(session);
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH)).thenReturn(true);

        service.logoutAll(identity);

        verify(accountEpochService).revoke(10001L);
        verify(sessionService, never()).kickAll(anyLong());
    }

    @Test
    void 新凭据登录可以用新epoch() {
        String newEpoch = "CCCCCCCCCCCCCCCCCCCCCC";
        when(accountEpochService.currentOrCreate(10001L)).thenReturn(newEpoch);
        when(accountEpochService.isCurrent(10001L, newEpoch)).thenReturn(true);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq("web"), anyString(), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("access-token", now.plusSeconds(1800),
                        "refresh-token", now.plusSeconds(604800)));

        LoginResult result = service.login(loginRequest(), "127.0.0.1");

        assertNotNull(result);
        assertEquals("ext-alice", result.getExternalId());
        ArgumentCaptor<SessionInfo> captor = ArgumentCaptor.forClass(SessionInfo.class);
        verify(sessionService).create(captor.capture());
        assertEquals(newEpoch, captor.getValue().getAccountEpoch());
    }

    @Test
    void me无内部ID() {
        String sid = "sid-me";
        AuthenticatedIdentity identity = new AuthenticatedIdentity("ext-alice", "web", sid, 10001L);
        SessionInfo session = buildSession(sid, "ext-alice", "web", "access-jti", "refresh-jti", futureExpire());

        mockValidDefaultGrant();
        when(sessionService.get(sid)).thenReturn(session);

        MeResponse response = service.me(identity);

        assertNotNull(response);
        assertEquals("ext-alice", response.externalId());
        assertEquals("web", response.clientCode());
        // MeResponse 不应包含内部用户 ID
        assertFalse(response.getClass().getDeclaredFields()[0].getName().equals("userId"));
    }

    @Test
    void issueForSession目标平台未授权拒绝() {
        String sourceSid = "sid-source";
        String targetClient = "mobile";
        SessionInfo source = buildSession(sourceSid, "ext-alice", "web", "access-jti", "refresh-jti", futureExpire());

        // 源会话有效
        mockValidDefaultGrant();
        when(sessionService.get(sourceSid)).thenReturn(source);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH)).thenReturn(true);

        // 目标客户端存在但未授权
        AuthClient targetAuthClient = new AuthClient();
        targetAuthClient.setApplicationCategory("OFFICIAL_NATIVE");
        targetAuthClient.setClientCode(targetClient);
        targetAuthClient.setStatus(1);
        when(authClientMapper.selectByClientCode(targetClient)).thenReturn(targetAuthClient);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, targetClient)).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.issueForSession(source, targetClient));
        assertEquals(30009, e.getCode());
        verify(sessionService, never()).create(any(SessionInfo.class));
        verify(tokenService, never()).issueTokenPair(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void issueForSession成功创建子会话() {
        String sourceSid = "sid-source-success";
        String targetClient = "mobile";
        SessionInfo source = buildSession(sourceSid, "ext-alice", "web", "access-jti", "refresh-jti", futureExpire());

        mockValidDefaultGrant();
        when(sessionService.get(sourceSid)).thenReturn(source);
        when(authUserMapper.selectByUsername("alice")).thenReturn(user);
        when(accountEpochService.isCurrent(10001L, FIXED_EPOCH)).thenReturn(true);

        AuthClient targetAuthClient = new AuthClient();
        targetAuthClient.setApplicationCategory("OFFICIAL_NATIVE");
        targetAuthClient.setClientCode(targetClient);
        targetAuthClient.setStatus(1);
        AuthPlatform targetPlatform = new AuthPlatform();
        targetPlatform.setUserId(10001L);
        targetPlatform.setClientCode(targetClient);
        targetPlatform.setStatus(1);
        when(authClientMapper.selectByClientCode(targetClient)).thenReturn(targetAuthClient);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, targetClient)).thenReturn(targetPlatform);
        when(tokenService.issueTokenPair(eq("ext-alice"), eq(targetClient), anyString(), anyString(), anyString()))
                .thenReturn(new TokenService.TokenPair("child-access", now.plusSeconds(1800),
                        "child-refresh", now.plusSeconds(604800)));

        LoginResult result = service.issueForSession(source, targetClient);

        assertNotNull(result);
        assertEquals("child-access", result.getAccessToken());
        assertEquals("ext-alice", result.getExternalId());
        ArgumentCaptor<SessionInfo> captor = ArgumentCaptor.forClass(SessionInfo.class);
        verify(sessionService).create(captor.capture());
        assertEquals(targetClient, captor.getValue().getClientCode());
        assertEquals(FIXED_EPOCH, captor.getValue().getAccountEpoch());
    }
    @Test
    void unclassifiedAndThirdPartyPasswordLoginRejectedBeforeCredentialLookup(){
        client.setApplicationCategory(null);
        assertThrows(BizException.class,()->service.login(loginRequest(),"127.0.0.1"));
        client.setApplicationCategory("THIRD_PARTY");
        assertThrows(BizException.class,()->service.login(loginRequest(),"127.0.0.1"));
        verifyNoInteractions(authUserMapper);
    }

}