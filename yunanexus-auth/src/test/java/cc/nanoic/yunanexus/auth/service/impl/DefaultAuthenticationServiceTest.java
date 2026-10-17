package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import cc.nanoic.yunanexus.auth.service.SessionService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAuthenticationServiceTest {

    private static final String VALID_EPOCH = "AAAAAAAAAAAAAAAAAAAAAA"; // 22 chars Base64URL
    private static final long NOW = 1700000000000L;
    private static final long FUTURE = NOW + 60_000L;

    @Mock private TokenService tokenService;
    @Mock private SessionService sessionService;
    @Mock private AccountEpochService accountEpochService;
    @Mock private AuthUserMapper authUserMapper;
    @Mock private AuthClientMapper authClientMapper;
    @Mock private AuthPlatformMapper authPlatformMapper;

    private Clock fixedClock;
    private DefaultAuthenticationService service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
        service = new DefaultAuthenticationService(
                tokenService, sessionService, accountEpochService,
                authUserMapper, authClientMapper, authPlatformMapper, fixedClock);
    }

    private SessionInfo validSession() {
        return new SessionInfo(
                "sid-123", 42L, "external-42", "user42",
                "clientA", "device-1", "access-jti-1", "refresh-jti-1",
                NOW - 1000, FUTURE, FUTURE, FUTURE + 60_000, VALID_EPOCH);
    }

    private AuthUser validUser() {
        AuthUser u = new AuthUser();
        u.setUserId(42L);
        u.setExternalId("external-42");
        u.setUsername("user42");
        u.setStatus(1);
        return u;
    }

    private AuthClient validClient() {
        AuthClient c = new AuthClient();
        c.setApplicationCategory("OFFICIAL_NATIVE");
        c.setClientCode("clientA");
        c.setStatus(1);
        return c;
    }

    private AuthPlatform validPlatform() {
        AuthPlatform p = new AuthPlatform();
        p.setUserId(42L);
        p.setClientCode("clientA");
        p.setStatus(1);
        return p;
    }

    private TokenService.TokenPayload accessPayload() {
        return new TokenService.TokenPayload("access-jti-1", "external-42", "clientA", "sid-123",
                Instant.ofEpochMilli(FUTURE));
    }

    private TokenService.TokenPayload refreshPayload() {
        return new TokenService.TokenPayload("refresh-jti-1", "external-42", "clientA", "sid-123",
                Instant.ofEpochMilli(FUTURE));
    }

    @Test
    void access_正常() {
        SessionInfo session = validSession();
        when(tokenService.verifyAccessToken("token")).thenReturn(accessPayload());
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "clientA")).thenReturn(validPlatform());

        AuthenticatedIdentity identity = service.authenticateAccess("token");

        assertNotNull(identity);
        assertEquals("external-42", identity.externalId());
        assertEquals("clientA", identity.clientCode());
        assertEquals("sid-123", identity.sid());
        assertEquals(42L, identity.userId());
        verify(accountEpochService, times(1)).isCurrent(42L, VALID_EPOCH);
    }

    @Test
    void refresh_正常() {
        SessionInfo session = validSession();
        when(tokenService.verifyRefreshToken("refresh-token")).thenReturn(refreshPayload());
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "clientA")).thenReturn(validPlatform());

        AuthenticatedIdentity identity = service.authenticateRefresh("refresh-token");

        assertNotNull(identity);
        assertEquals(42L, identity.userId());
    }

    @Test
    void 旧会话missingEpoch_拒绝() {
        SessionInfo session = validSession();
        session.setAccountEpoch(null); // 缺失 epoch
        when(sessionService.get("sid-123")).thenReturn(session);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    void epoch失效_拒绝() {
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.TOKEN_REVOKED.getCode(), ex.getCode());
    }

    @Test
    void Redis故障_传播() {
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH))
                .thenThrow(new RuntimeException("Redis down"));

        assertThrows(RuntimeException.class, () -> service.requireSession("sid-123"));
    }

    @Test
    void uid错配_拒绝() {
        SessionInfo session = validSession();
        AuthUser user = validUser();
        user.setUserId(999L); // 不匹配
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    void externalId错配_拒绝() {
        SessionInfo session = validSession();
        AuthUser user = validUser();
        user.setExternalId("other-external");
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    void clientCode错配_拒绝() {
        SessionInfo session = validSession();
        session.setClientCode("other-client");
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("other-client")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.CLIENT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void sid错配_拒绝() {
        SessionInfo session = validSession();
        session.setSid("other-sid");
        when(sessionService.get("sid-123")).thenReturn(session);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    void jti错配_access拒绝() {
        SessionInfo session = validSession();
        session.setAccessJti("other-jti");
        when(tokenService.verifyAccessToken("token")).thenReturn(accessPayload());
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "clientA")).thenReturn(validPlatform());

        BizException ex = assertThrows(BizException.class,
                () -> service.authenticateAccess("token"));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    void access用refreshJTI_拒绝() {
        SessionInfo session = validSession();
        // 将 access payload 的 jti 设为 refresh jti
        TokenService.TokenPayload payload = new TokenService.TokenPayload(
                "refresh-jti-1", "external-42", "clientA", "sid-123", Instant.ofEpochMilli(FUTURE));
        when(tokenService.verifyAccessToken("token")).thenReturn(payload);
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "clientA")).thenReturn(validPlatform());

        BizException ex = assertThrows(BizException.class,
                () -> service.authenticateAccess("token"));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    void 账号status0_拒绝() {
        AuthUser user = validUser();
        user.setStatus(0);
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void 账号status2_拒绝() {
        AuthUser user = validUser();
        user.setStatus(2);
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.USER_LOCKED.getCode(), ex.getCode());
    }

    @Test
    void 账号statusNull_拒绝() {
        AuthUser user = validUser();
        user.setStatus(null);
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void 账号status99_拒绝() {
        AuthUser user = validUser();
        user.setStatus(99);
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void client禁用_拒绝() {
        AuthClient client = validClient();
        client.setStatus(0);
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(client);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.CLIENT_DISABLED.getCode(), ex.getCode());
    }

    @Test
    void client缺失_拒绝() {
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.CLIENT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void grant撤销_拒绝() {
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "clientA")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.PLATFORM_AUTH_REVOKED.getCode(), ex.getCode());
    }

    @Test
    void 缺失session_拒绝() {
        when(sessionService.get("missing")).thenReturn(null);
        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("missing"));
        assertEquals(ErrorCode.SESSION_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 过期session_拒绝() {
        SessionInfo session = validSession();
        session.setExpireAtMillis(NOW - 1000);
        when(sessionService.get("sid-123")).thenReturn(session);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.TOKEN_INVALID.getCode(), ex.getCode());
    }

    @Test
    void requireGrant_目标client检查_不去重建epoch() {
        SessionInfo session = validSession();
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(authClientMapper.selectByClientCode("targetClient")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "targetClient")).thenReturn(validPlatform());

        AuthClient result = service.requireGrant(session, "targetClient");

        assertNotNull(result);
        assertEquals("clientA", result.getClientCode());
        verify(accountEpochService, times(1)).isCurrent(42L, VALID_EPOCH);
        verify(accountEpochService, never()).currentOrCreate(anyLong());
    }

    @Test
    void 每请求都调用epoch_未缓存() {
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "clientA")).thenReturn(validPlatform());

        service.requireSession("sid-123");
        service.requireSession("sid-123");

        verify(accountEpochService, times(2)).isCurrent(42L, VALID_EPOCH);
    }

    @Test
    void 用户不存在_拒绝() {
        SessionInfo session = validSession();
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> service.requireSession("sid-123"));
        assertEquals(ErrorCode.AUTH_USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void access_refresh_Identity包含internaluid_不向外暴露() {
        // 测试仅关注内部字段；外部响应不在本类测试范围
        SessionInfo session = validSession();
        when(tokenService.verifyAccessToken("token")).thenReturn(accessPayload());
        when(sessionService.get("sid-123")).thenReturn(session);
        when(authUserMapper.selectByUsername("user42")).thenReturn(validUser());
        when(accountEpochService.isCurrent(42L, VALID_EPOCH)).thenReturn(true);
        when(authClientMapper.selectByClientCode("clientA")).thenReturn(validClient());
        when(authPlatformMapper.selectByUserIdAndClient(42L, "clientA")).thenReturn(validPlatform());

        AuthenticatedIdentity identity = service.authenticateAccess("token");
        assertTrue(identity.userId() == 42L);
        // 不做响应序列化断言，由 web 层测试覆盖
    }
}
