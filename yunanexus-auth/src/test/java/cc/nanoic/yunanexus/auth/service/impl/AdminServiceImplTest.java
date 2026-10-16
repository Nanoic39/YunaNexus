package cc.nanoic.yunanexus.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.auth.domain.AppealView;
import cc.nanoic.yunanexus.auth.domain.UserView;
import cc.nanoic.yunanexus.auth.entity.AuthUnlockAppeal;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthUnlockAppealMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import cc.nanoic.yunanexus.auth.service.LoginAttemptService;
import cc.nanoic.yunanexus.auth.service.SessionService;
import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 认证中心管理后台服务单元测试。
 *
 * <p>覆盖申诉分页与审核幂等、用户列表过滤与分页、启用/禁用/解锁/踢下线的状态与副作用。
 *
 * @author yunanexus
 */
class AdminServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-12T08:00:00Z");

    private AuthUnlockAppealMapper appealMapper;
    private AuthUserMapper userMapper;
    private LoginAttemptService loginAttemptService;
    private SessionService sessionService;
    private AccountEpochService accountEpochService;
    private AdminServiceImpl service;

    @BeforeEach
    void setUp() {
        appealMapper = mock(AuthUnlockAppealMapper.class);
        userMapper = mock(AuthUserMapper.class);
        loginAttemptService = mock(LoginAttemptService.class);
        sessionService = mock(SessionService.class);
        accountEpochService = mock(AccountEpochService.class);
        service =
                new AdminServiceImpl(
                        appealMapper,
                        userMapper,
                        loginAttemptService,
                        sessionService,
                        accountEpochService,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void roleAndPermissionFiltersApplyBeforePagination() {
        var roles = mock(cc.nanoic.yunanexus.auth.client.RootAdminClient.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "rootRoles", roles);
        when(roles.filterUsers(2L, 3L))
                .thenReturn(cc.nanoic.yunanexus.common.core.model.Result.success(List.of(7L, 8L)));
        when(userMapper.countFiltered("ali", 1, List.of(7L, 8L))).thenReturn(1L);
        when(userMapper.selectFiltered("ali", 1, List.of(7L, 8L), 0, 10))
                .thenReturn(List.of(user(7L, "alice", 1)));
        var page = service.listUsers(" ali ", 1, 1, 10, 2L, 3L);
        assertEquals(1, page.getTotal());
        assertEquals(7L, page.getRecords().get(0).getUserId());
        verify(userMapper, never()).selectPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    void emptyPermissionMatchDoesNotReturnAllUsers() {
        var roles = mock(cc.nanoic.yunanexus.auth.client.RootAdminClient.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "rootRoles", roles);
        when(roles.filterUsers(2L, null))
                .thenReturn(cc.nanoic.yunanexus.common.core.model.Result.success(List.of()));
        assertEquals(0, service.listUsers(null, null, 1, 20, 2L, null).getTotal());
        verifyNoInteractions(userMapper);
    }

    // ---------- 申诉分页与查询 ----------

    @Test
    void 无记录时申诉列表返回空且不查询明细() {
        when(appealMapper.countByStatus(0)).thenReturn(0L);

        PageResult<AppealView> result = service.listAppeals(0, 1, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(appealMapper, never()).selectPage(any(), anyInt(), anyInt());
    }

    @Test
    void 申诉列表按页码计算偏移并转换视图() {
        when(appealMapper.countByStatus(null)).thenReturn(25L);
        when(appealMapper.selectPage(null, 20, 20)).thenReturn(List.of(appeal(1L, 100L, 0)));

        PageResult<AppealView> result = service.listAppeals(null, 2, 20);

        assertEquals(25L, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(20, result.getSize());
        assertEquals(100L, result.getRecords().get(0).getUserId());
        verify(appealMapper).selectPage(null, 20, 20);
    }

    @Test
    void 申诉列表限制最大每页并规整页码() {
        when(appealMapper.countByStatus(null)).thenReturn(1L);
        when(appealMapper.selectPage(null, 0, 100)).thenReturn(List.of(appeal(1L, 1L, 0)));

        PageResult<AppealView> result = service.listAppeals(null, 0, 500);

        assertEquals(1, result.getPage());
        assertEquals(100, result.getSize());
        verify(appealMapper).selectPage(null, 0, 100);
    }

    @Test
    void 查询不存在的申诉抛资源不存在() {
        when(appealMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getAppeal(9L));

        assertEquals(ErrorCode.APPEAL_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---------- 申诉审核 ----------

    @Test
    void 通过申诉会审核并解锁关联账号() {
        when(appealMapper.selectById(5L)).thenReturn(appeal(5L, 42L, 0));
        when(appealMapper.audit(eq(5L), eq(1), anyString(), any(), any())).thenReturn(1);
        when(userMapper.selectByUserId(42L)).thenReturn(user(42L, "alice", 0));

        service.approveAppeal(5L, "admin-1", "ok");

        verify(appealMapper)
                .audit(
                        eq(5L),
                        eq(1),
                        eq("admin-1"),
                        eq("ok"),
                        eq(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)));
        verify(userMapper).updateStatusAndLock(42L, 1, null, null);
        verify(loginAttemptService).reset("alice");
    }

    @Test
    void 通过申诉处理人缺省记为admin() {
        when(appealMapper.selectById(5L)).thenReturn(appeal(5L, 42L, 0));
        when(appealMapper.audit(eq(5L), eq(1), eq("admin"), any(), any())).thenReturn(1);
        when(userMapper.selectByUserId(42L)).thenReturn(user(42L, "alice", 0));

        service.approveAppeal(5L, "   ", null);

        verify(appealMapper).audit(eq(5L), eq(1), eq("admin"), isNull(), any());
    }

    @Test
    void 审核非待处理申诉抛已处理() {
        when(appealMapper.selectById(5L)).thenReturn(appeal(5L, 42L, 1));

        BizException ex =
                assertThrows(BizException.class, () -> service.approveAppeal(5L, "a", null));

        assertEquals(ErrorCode.APPEAL_ALREADY_AUDITED.getCode(), ex.getCode());
        verify(appealMapper, never()).audit(anyLong(), anyInt(), any(), any(), any());
    }

    @Test
    void 条件更新未命中时抛已处理且不解锁() {
        when(appealMapper.selectById(5L)).thenReturn(appeal(5L, 42L, 0));
        when(appealMapper.audit(eq(5L), eq(1), anyString(), any(), any())).thenReturn(0);

        BizException ex =
                assertThrows(BizException.class, () -> service.approveAppeal(5L, "a", null));

        assertEquals(ErrorCode.APPEAL_ALREADY_AUDITED.getCode(), ex.getCode());
        verify(userMapper, never()).updateStatusAndLock(anyLong(), anyInt(), any(), any());
    }

    @Test
    void 驳回申诉仅标记状态不解锁账号() {
        when(appealMapper.selectById(5L)).thenReturn(appeal(5L, 42L, 0));
        when(appealMapper.audit(eq(5L), eq(2), anyString(), any(), any())).thenReturn(1);

        service.rejectAppeal(5L, "auditor", "no");

        verify(appealMapper).audit(eq(5L), eq(2), eq("auditor"), eq("no"), any());
        verifyNoInteractions(userMapper);
    }

    // ---------- 用户管理 ----------

    @Test
    void 用户列表去除关键字首尾空白并按条件查询() {
        when(userMapper.countByCondition("ali", 1)).thenReturn(1L);
        when(userMapper.selectPage("ali", 1, 0, 10)).thenReturn(List.of(user(7L, "alice", 1)));

        PageResult<UserView> result = service.listUsers("  ali  ", 1, 1, 10);

        assertEquals(1L, result.getTotal());
        assertEquals(7L, result.getRecords().get(0).getUserId());
        verify(userMapper).selectPage("ali", 1, 0, 10);
    }

    @Test
    void 用户列表空白关键字视为不过滤() {
        when(userMapper.countByCondition(null, null)).thenReturn(0L);

        PageResult<UserView> result = service.listUsers("   ", null, 1, 20);

        assertEquals(0L, result.getTotal());
        verify(userMapper).countByCondition(null, null);
        verify(userMapper, never()).selectPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    void 查询不存在的用户抛资源不存在() {
        when(userMapper.selectByUserId(7L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getUser(7L));

        assertEquals(ErrorCode.ADMIN_USER_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 启用账号置正常并清零失败计数() {
        when(userMapper.selectByUserId(7L)).thenReturn(user(7L, "alice", 0));

        service.enableUser(7L);

        verify(userMapper).updateStatusAndLock(7L, 1, null, null);
        verify(loginAttemptService).reset("alice");
    }

    @Test
    void 禁用账号置禁用且不清零失败计数() {
        when(userMapper.selectByUserId(7L)).thenReturn(user(7L, "alice", 1));

        service.disableUser(7L);

        verify(userMapper).updateStatusAndLock(7L, 0, null, null);
        verifyNoInteractions(loginAttemptService);
    }

    @Test
    void 解锁账号置正常并清零失败计数() {
        when(userMapper.selectByUserId(7L)).thenReturn(user(7L, "alice", 2));

        service.unlockUser(7L);

        verify(userMapper).updateStatusAndLock(7L, 1, null, null);
        verify(loginAttemptService).reset("alice");
    }

    @Test
    void 踢下线撤销全部会话与纪元() {
        var roles = mock(cc.nanoic.yunanexus.auth.client.RootAdminClient.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "rootRoles", roles);
        when(roles.superCheck(7L))
                .thenReturn(cc.nanoic.yunanexus.common.core.model.Result.success(false));
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        context.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin",
                        null,
                        List.of(
                                new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(userMapper.selectByUserId(7L)).thenReturn(user(7L, "alice", 1));
        when(sessionService.kickAll(7L)).thenReturn(3);

        service.kickUser(7L);

        verify(sessionService).kickAll(7L);
        verify(accountEpochService).revoke(7L);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void 启用不存在的用户抛资源不存在且不更新() {
        when(userMapper.selectByUserId(7L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.enableUser(7L));

        assertEquals(ErrorCode.ADMIN_USER_NOT_FOUND.getCode(), ex.getCode());
        verify(userMapper, never()).updateStatusAndLock(anyLong(), anyInt(), any(), any());
    }

    private AuthUnlockAppeal appeal(long id, long userId, int status) {
        AuthUnlockAppeal appeal = new AuthUnlockAppeal();
        appeal.setId(id);
        appeal.setUserId(userId);
        appeal.setUsername("alice");
        appeal.setStatus(status);
        return appeal;
    }

    private AuthUser user(long userId, String username, int status) {
        AuthUser user = new AuthUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setStatus(status);
        return user;
    }

    @Test
    void 管理员不能禁用自己() {
        var context =
                org.springframework.security.core.context.SecurityContextHolder
                        .createEmptyContext();
        context.setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity(
                                "ext", "web", "sid", 7L),
                        null));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            assertEquals(
                    403, assertThrows(BizException.class, () -> service.disableUser(7L)).getCode());
            verifyNoInteractions(userMapper);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
