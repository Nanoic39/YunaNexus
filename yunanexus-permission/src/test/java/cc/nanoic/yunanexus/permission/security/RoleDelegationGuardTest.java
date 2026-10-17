package cc.nanoic.yunanexus.permission.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;
import cc.nanoic.yunanexus.permission.domain.DataScopeResponse;
import cc.nanoic.yunanexus.permission.entity.*;
import cc.nanoic.yunanexus.permission.mapper.*;
import cc.nanoic.yunanexus.permission.service.DataScopeService;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

import java.util.List;

class RoleDelegationGuardTest {
    AdminBearerVerifier verifier;
    PermPermissionMapper permissions;
    PermRolePermissionMapper rolePermissions;
    PermRoleMapper roles;
    PermRoleDeptMapper roleDepts;
    PermRoleMenuMapper roleMenus;
    PermUserRoleMapper userRoles;
    DataScopeService scopes;
    RoleDelegationGuard guard;

    @BeforeEach
    void setup() {
        verifier = mock(AdminBearerVerifier.class);
        permissions = mock(PermPermissionMapper.class);
        rolePermissions = mock(PermRolePermissionMapper.class);
        roles = mock(PermRoleMapper.class);
        roleDepts = mock(PermRoleDeptMapper.class);
        roleMenus = mock(PermRoleMenuMapper.class);
        userRoles = mock(PermUserRoleMapper.class);
        scopes = mock(DataScopeService.class);
        guard =
                new RoleDelegationGuard(
                        verifier,
                        permissions,
                        rolePermissions,
                        roles,
                        roleDepts,
                        roleMenus,
                        userRoles,
                        scopes);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer actor");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(verifier.actor("Bearer actor")).thenReturn(new AdminBearerVerifier.Actor(1, false));
        var p = new PermPermission();
        p.setId(10L);
        when(permissions.selectEffectiveByUserId(1)).thenReturn(List.of(p));
        var role = new PermRole();
        role.setId(9L);
        role.setDataScope(4);
        when(roles.selectById(9L)).thenReturn(role);
        when(rolePermissions.selectPermissionIdsByRoleId(9)).thenReturn(List.of(10L));
        when(scopes.resolve(1)).thenReturn(DataScopeResponse.of(1, false, List.of(3L), 1L));
    }

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void cannotGrantUnownedPermission() {
        assertThrows(BizException.class, () -> guard.permissions(9, List.of(11L)));
    }

    @Test
    void cannotEditRoleAlreadyAboveOperator() {
        when(rolePermissions.selectPermissionIdsByRoleId(9)).thenReturn(List.of(11L));
        assertThrows(BizException.class, () -> guard.manage(9));
    }

    @Test
    void mayDelegateExactOwnedSubset() {
        assertDoesNotThrow(() -> guard.permissions(9, List.of(10L)));
    }

    @Test
    void cannotExpandDataOrDepartmentScope() {
        assertThrows(BizException.class, () -> guard.scope(1));
        assertThrows(BizException.class, () -> guard.departments(List.of(4L)));
        assertDoesNotThrow(() -> guard.departments(List.of(3L)));
    }

    @Test
    void assignmentCannotSmuggleStrongerRole() {
        when(rolePermissions.selectPermissionIdsByRoleId(9)).thenReturn(List.of(11L));
        assertThrows(BizException.class, () -> guard.assignment(2, List.of(9L)));
    }

    @Test
    void unknownActorFailsClosed() {
        when(verifier.actor("Bearer actor")).thenReturn(null);
        assertThrows(BizException.class, () -> guard.scope(4));
    }

    @Test
    void superAdminCanDelegateAnyPermission() {
        when(verifier.actor("Bearer actor")).thenReturn(new AdminBearerVerifier.Actor(1, true));
        assertDoesNotThrow(() -> guard.permissions(9, List.of(11L)));
    }
    @Test
    void cannotAssignRoleWithMenusOutsideOperatorScope() {
        when(roleMenus.selectMenuIdsByRoleId(9)).thenReturn(List.of(20L));
        assertEquals(403, assertThrows(BizException.class, () -> guard.assignment(2, List.of(9L))).getCode());
    }

}
