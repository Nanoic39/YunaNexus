package cc.nanoic.yunanexus.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.auth.client.RootAdminClient;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.*;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.*;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.List;

class AdminKickProtectionTest {
    RootAdminClient roles;
    SessionService sessions;
    AccountEpochService epochs;
    AdminServiceImpl service;

    @BeforeEach
    void setup() {
        roles = mock(RootAdminClient.class);
        sessions = mock(SessionService.class);
        epochs = mock(AccountEpochService.class);
        var users = mock(AuthUserMapper.class);
        var target = new AuthUser();
        target.setUserId(9L);
        when(users.selectByUserId(9L)).thenReturn(target);
        service =
                new AdminServiceImpl(
                        mock(AuthUnlockAppealMapper.class),
                        users,
                        mock(LoginAttemptService.class),
                        sessions,
                        epochs,
                        Clock.systemUTC());
        ReflectionTestUtils.setField(service, "rootRoles", roles);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void actor(boolean superAdmin) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthenticatedIdentity("actor", "web", "sid", 1L),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(roles.check(1L)).thenReturn(Result.success(true));
        when(roles.superCheck(1L)).thenReturn(Result.success(superAdmin));
    }

    void denied() {
        assertThrows(BizException.class, () -> service.kickUser(9L));
        verifyNoInteractions(sessions, epochs);
    }

    @Test
    void noActorCanKickSuperAdmin() {
        for (boolean privileged : List.of(false, true)) {
            actor(privileged);
            when(roles.superCheck(9L)).thenReturn(Result.success(true));
            denied();
        }
    }

    @Test
    void emergencyKeyCannotKickSuperAdmin() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(roles.superCheck(9L)).thenReturn(Result.success(true));
        denied();
    }

    @Test
    void peerAdminCannotKickAdmin() {
        actor(false);
        when(roles.superCheck(9L)).thenReturn(Result.success(false));
        when(roles.check(9L)).thenReturn(Result.success(true));
        denied();
    }

    @Test
    void roleFailureDoesNotRevokeAnything() {
        actor(true);
        when(roles.superCheck(9L)).thenReturn(Result.success(null));
        denied();
    }

    @Test
    void ordinaryActorCannotBypassService() {
        actor(false);
        when(roles.superCheck(9L)).thenReturn(Result.success(false));
        when(roles.check(1L)).thenReturn(Result.success(false));
        denied();
    }

    @Test
    void superAdminMayKickOrdinaryAdmin() {
        actor(true);
        when(roles.superCheck(9L)).thenReturn(Result.success(false));
        service.kickUser(9L);
        verify(sessions).kickAll(9L);
        verify(epochs).revoke(9L);
    }

    @Test
    void adminMayKickOrdinaryUser() {
        actor(false);
        when(roles.superCheck(9L)).thenReturn(Result.success(false));
        when(roles.check(9L)).thenReturn(Result.success(false));
        service.kickUser(9L);
        verify(sessions).kickAll(9L);
        verify(epochs).revoke(9L);
    }
}
