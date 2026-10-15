package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.permission.domain.CurrentUserView;
import cc.nanoic.yunanexus.permission.domain.MenuTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.MenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CurrentUserServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class CurrentUserServiceImplTest {

    private PermUserRoleMapper userRoleMapper;
    private PermRoleMapper roleMapper;
    private PermPermissionMapper permissionMapper;
    private MenuService menuService;
    private CurrentUserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRoleMapper = mock(PermUserRoleMapper.class);
        roleMapper = mock(PermRoleMapper.class);
        permissionMapper = mock(PermPermissionMapper.class);
        menuService = mock(MenuService.class);
        service = new CurrentUserServiceImpl(userRoleMapper, roleMapper, permissionMapper, menuService);
    }

    private PermRole role(long id, String code) {
        PermRole role = new PermRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName("角色" + id);
        role.setStatus(PermRole.STATUS_ENABLED);
        return role;
    }

    private PermPermission permission(String code) {
        PermPermission permission = new PermPermission();
        permission.setPermCode(code);
        permission.setStatus(PermPermission.STATUS_ENABLED);
        return permission;
    }

    @Test
    void 用户无角色时仅返回用户ID与空集合() {
        when(userRoleMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(1L)).thenReturn(List.of());
        when(menuService.userMenuTree(1L)).thenReturn(List.of());

        CurrentUserView view = service.load(1L);

        assertEquals(1L, view.getUserId());
        assertTrue(view.getRoles().isEmpty());
        assertTrue(view.getPermissions().isEmpty());
        assertTrue(view.getMenus().isEmpty());
        verify(roleMapper, never()).selectList(any());
    }

    @Test
    void 聚合启用角色权限并集与菜单树() {
        when(userRoleMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of(5L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(5L, "admin")));
        when(permissionMapper.selectEffectiveByUserId(1L))
                .thenReturn(List.of(permission("file:write"), permission("file:read"), permission("file:read")));
        MenuTreeNode node = new MenuTreeNode();
        node.setId(1L);
        node.setMenuCode("file");
        when(menuService.userMenuTree(1L)).thenReturn(List.of(node));

        CurrentUserView view = service.load(1L);

        assertEquals(1, view.getRoles().size());
        assertEquals("admin", view.getRoles().get(0).getRoleCode());
        assertEquals(List.of("file:read", "file:write"), view.getPermissions());
        assertEquals(1, view.getMenus().size());
        assertEquals("file", view.getMenus().get(0).getMenuCode());
    }

    @Test
    void 用户角色被禁用时角色为空() {
        when(userRoleMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of(5L));
        when(roleMapper.selectList(any())).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(1L)).thenReturn(List.of());
        when(menuService.userMenuTree(1L)).thenReturn(List.of());

        CurrentUserView view = service.load(1L);

        assertTrue(view.getRoles().isEmpty());
    }
}
