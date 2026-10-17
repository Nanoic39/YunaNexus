package cc.nanoic.yunanexus.permission.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;
import cc.nanoic.yunanexus.permission.domain.*;
import cc.nanoic.yunanexus.permission.entity.*;
import cc.nanoic.yunanexus.permission.mapper.*;
import cc.nanoic.yunanexus.permission.service.DataScopeService;
import cc.nanoic.yunanexus.permission.service.impl.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

class CatalogMutationGuardTest {
    AdminBearerVerifier verifier;
    PermPermissionMapper permissions;
    PermRolePermissionMapper rolePermissions;
    PermRoleMenuMapper roleMenus;
    PermMenuMapper menus;
    PermissionServiceImpl permissionService;
    MenuServiceImpl menuService;

    @BeforeEach
    void setup() {
        verifier = mock(AdminBearerVerifier.class);
        permissions = mock(PermPermissionMapper.class);
        rolePermissions = mock(PermRolePermissionMapper.class);
        roleMenus = mock(PermRoleMenuMapper.class);
        menus = mock(PermMenuMapper.class);
        var roles = mock(PermRoleMapper.class);
        var bindings = mock(PermUserRoleMapper.class);
        var departments = mock(PermRoleDeptMapper.class);
        var scopes = mock(DataScopeService.class);
        var role = new PermRole(); role.setId(2L); role.setRoleCode("admin"); role.setDataScope(4);
        when(roles.selectById(2L)).thenReturn(role);
        var stronger = new PermRole(); stronger.setId(3L); stronger.setRoleCode("restricted"); stronger.setDataScope(4);
        when(roles.selectById(3L)).thenReturn(stronger);
        when(rolePermissions.selectPermissionIdsByRoleId(3)).thenReturn(List.of(99L));
        var owned = new PermPermission(); owned.setId(10L);
        when(permissions.selectEffectiveByUserId(1)).thenReturn(List.of(owned));
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer actor");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(verifier.actor("Bearer actor")).thenReturn(new AdminBearerVerifier.Actor(1, false));
        var guard = new CatalogMutationGuard(rolePermissions, roleMenus,
                new PrivilegedRoleGuard(verifier, roles, bindings),
                new RoleDelegationGuard(verifier, permissions, rolePermissions, roles, departments, roleMenus, bindings, scopes));
        permissionService = new PermissionServiceImpl(permissions, rolePermissions, guard);
        menuService = new MenuServiceImpl(menus, roleMenus, bindings, roles, guard);
    }

    @AfterEach
    void clear() { RequestContextHolder.resetRequestAttributes(); }

    void bindPermission(long role) {
        var binding = new PermRolePermission(); binding.setRoleId(role); binding.setPermissionId(10L);
        when(rolePermissions.selectList(any())).thenReturn(List.of(binding));
    }
    void bindMenu(long role) {
        var binding = new PermRoleMenu(); binding.setRoleId(role); binding.setMenuId(20L);
        when(roleMenus.selectList(any())).thenReturn(List.of(binding));
    }

    @Test
    void ordinaryAdminCannotDisableOrDeleteDefaultRolePermission() {
        bindPermission(2);
        var request = new PermissionSaveRequest(); request.setStatus(0);
        assertEquals(403, assertThrows(BizException.class, () -> permissionService.update(10, request)).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> permissionService.delete(10)).getCode());
        verify(permissions, never()).updateById(any(PermPermission.class));
        verify(permissions, never()).deleteById(anyLong());
        verify(rolePermissions, never()).delete(any());
    }

    @Test
    void ordinaryAdminCannotChangeMenuUsedByStrongerRole() {
        bindMenu(3);
        assertEquals(403, assertThrows(BizException.class, () -> menuService.update(20, new MenuSaveRequest())).getCode());
        assertEquals(403, assertThrows(BizException.class, () -> menuService.delete(20)).getCode());
        verify(menus, never()).updateById(any(PermMenu.class));
        verify(menus, never()).deleteById(anyLong());
        verify(roleMenus, never()).delete(any());
    }

    @Test
    void permissionOfStrongerCustomRoleCannotBeDeletedIndirectly() {
        bindPermission(3);
        assertEquals(403, assertThrows(BizException.class, () -> permissionService.delete(10)).getCode());
        verify(rolePermissions, never()).delete(any());
    }

    @Test
    void superAdminCanMaintainDefaultMenu() {
        bindMenu(2);
        when(verifier.actor("Bearer actor")).thenReturn(new AdminBearerVerifier.Actor(1, true));
        var menu = new PermMenu(); menu.setId(20L);
        when(menus.selectById(20L)).thenReturn(menu);
        assertDoesNotThrow(() -> menuService.delete(20));
        verify(menus).deleteById(20L);
    }

    @Test
    void unboundCatalogRemainsEditable() {
        var permission = new PermPermission(); permission.setId(10L);
        when(permissions.selectById(10L)).thenReturn(permission);
        var request = new PermissionSaveRequest(); request.setDescription("unbound draft");
        permissionService.update(10, request);
        assertEquals("unbound draft", permission.getDescription());
        verify(permissions).updateById(permission);
    }
}
