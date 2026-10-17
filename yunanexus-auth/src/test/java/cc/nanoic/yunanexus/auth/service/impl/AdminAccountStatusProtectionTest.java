package cc.nanoic.yunanexus.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cc.nanoic.yunanexus.auth.client.RootAdminClient;
import cc.nanoic.yunanexus.auth.entity.*;
import cc.nanoic.yunanexus.auth.mapper.*;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.*;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminAccountStatusProtectionTest {
    RootAdminClient roles;
    AuthUserMapper users;
    AuthUnlockAppealMapper appeals;
    LoginAttemptService attempts;
    AdminServiceImpl service;

    @BeforeEach
    void setup() {
        roles = mock(RootAdminClient.class); users = mock(AuthUserMapper.class);
        appeals = mock(AuthUnlockAppealMapper.class); attempts = mock(LoginAttemptService.class);
        var target = new AuthUser(); target.setUserId(9L); target.setUsername("target");
        when(users.selectByUserId(9L)).thenReturn(target);
        service = new AdminServiceImpl(appeals, users, attempts, mock(SessionService.class), mock(AccountEpochService.class), Clock.systemUTC());
        ReflectionTestUtils.setField(service, "rootRoles", roles);
        when(roles.superCheck(9L)).thenReturn(Result.success(false));
        when(roles.check(9L)).thenReturn(Result.success(false));
    }
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }
    void actor(boolean superAdmin) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedIdentity("actor", "web", "sid", 1L), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(roles.check(1L)).thenReturn(Result.success(true));
        when(roles.superCheck(1L)).thenReturn(Result.success(superAdmin));
    }
    void emergency() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    void noWrites() {
        verify(users, never()).updateStatusAndLock(anyLong(), anyInt(), any(), any());
        verifyNoInteractions(attempts);
    }
    @Test
    void nobodyCanDisableSuperAdminToBypassKickProtection() {
        when(roles.superCheck(9L)).thenReturn(Result.success(true));
        actor(false); assertEquals(403, assertThrows(BizException.class, () -> service.disableUser(9)).getCode());
        actor(true); assertEquals(403, assertThrows(BizException.class, () -> service.disableUser(9)).getCode());
        emergency(); assertEquals(403, assertThrows(BizException.class, () -> service.disableUser(9)).getCode());
        noWrites();
    }
    @Test
    void ordinaryAdminCannotChangePeerStatus() {
        actor(false); when(roles.check(9L)).thenReturn(Result.success(true));
        assertEquals(403, assertThrows(BizException.class, () -> service.disableUser(9)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.enableUser(9)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.unlockUser(9)).getCode());
        noWrites();
    }
    @Test
    void peerAppealIsRejectedBeforeAuditIsConsumed() {
        actor(false); when(roles.check(9L)).thenReturn(Result.success(true));
        var appeal = new AuthUnlockAppeal(); appeal.setId(8L); appeal.setUserId(9L); appeal.setStatus(0);
        when(appeals.selectById(8L)).thenReturn(appeal);
        assertEquals(403, assertThrows(BizException.class, () -> service.approveAppeal(8, "operator", "ok")).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> service.rejectAppeal(8, "operator", "no")).getCode());
        verify(appeals, never()).audit(anyLong(), anyInt(), any(), any(), any()); noWrites();
    }
    @Test
    void roleLookupFailureLeavesStatusUnchanged() {
        actor(true); when(roles.superCheck(9L)).thenReturn(Result.success(null));
        assertThrows(BizException.class, () -> service.disableUser(9));
        assertThrows(BizException.class, () -> service.unlockUser(9)); noWrites();
    }
    @Test
    void superAdminMayMaintainOrdinaryAdminStatus() {
        actor(true); when(roles.check(9L)).thenReturn(Result.success(true));
        service.disableUser(9); verify(users).updateStatusAndLock(9L, 0, null, null);
        service.unlockUser(9); verify(users).updateStatusAndLock(9L, 1, null, null);
    }
    @Test
    void ordinaryAdminMayMaintainOrdinaryUserStatus() {
        actor(false); service.disableUser(9); service.enableUser(9);
        verify(users).updateStatusAndLock(9L, 0, null, null);
        verify(users).updateStatusAndLock(9L, 1, null, null);
    }
    @Test
    void emergencyMayRecoverSuperAdminWithoutDisablingIt() {
        emergency(); when(roles.superCheck(9L)).thenReturn(Result.success(true));
        service.unlockUser(9); verify(users).updateStatusAndLock(9L, 1, null, null);
    }
}
