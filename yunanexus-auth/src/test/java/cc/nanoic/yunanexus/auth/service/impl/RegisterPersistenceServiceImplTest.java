package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthRegisterOutboxMapper;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 注册授权的最小范围与失败边界。 */
class RegisterPersistenceServiceImplTest {
    private final AuthUserMapper users = mock(AuthUserMapper.class);
    private final AuthPlatformMapper platforms = mock(AuthPlatformMapper.class);
    private final AuthRegisterOutboxMapper outbox = mock(AuthRegisterOutboxMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
    private final RegisterPersistenceServiceImpl service = new RegisterPersistenceServiceImpl(users, outbox, platforms, clock);
    private final AuthUser user = new AuthUser();

    @BeforeEach
    void setup() {
        user.setUserId(123L);
        when(users.insert(any(AuthUser.class))).thenReturn(1);
    }

    @Test
    void registrationGrantsOnlyWebAndRetainsOutbox() {
        when(platforms.insert(any(AuthPlatform.class))).thenReturn(1);
        when(outbox.insert(any(cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox.class))).thenReturn(1);
        assertSame(user, service.persistWithOutbox(user));
        ArgumentCaptor<AuthPlatform> grant = ArgumentCaptor.forClass(AuthPlatform.class);
        verify(platforms, times(1)).insert(grant.capture());
        assertEquals("web", grant.getValue().getClientCode());
        assertEquals(user.getUserId(), grant.getValue().getUserId());
        assertEquals(1, grant.getValue().getStatus());
        assertNotNull(grant.getValue().getAuthorizedAt());
        verify(outbox).insert(any(cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox.class));
    }

    @Test
    void failedPortalGrantRejectsRegistration() {
        when(platforms.insert(any(AuthPlatform.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.persistWithOutbox(user));
        verifyNoInteractions(outbox);
    }

    @Test
    void failedUserInsertCannotGrantPlatformAccess() {
        when(users.insert(any(AuthUser.class))).thenReturn(0);
        assertThrows(BizException.class, () -> service.persistWithOutbox(user));
        verifyNoInteractions(platforms, outbox);
    }
}
