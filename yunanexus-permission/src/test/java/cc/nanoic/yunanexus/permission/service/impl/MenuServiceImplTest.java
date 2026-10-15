package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.MenuSaveRequest;
import cc.nanoic.yunanexus.permission.domain.MenuTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermMenu;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.entity.PermRoleMenu;
import cc.nanoic.yunanexus.permission.mapper.PermMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MenuServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class MenuServiceImplTest {

    private PermMenuMapper menuMapper;
    private PermRoleMenuMapper roleMenuMapper;
    private PermUserRoleMapper userRoleMapper;
    private PermRoleMapper roleMapper;
    private MenuServiceImpl service;

    @BeforeEach
    void setUp() {
        menuMapper = mock(PermMenuMapper.class);
        roleMenuMapper = mock(PermRoleMenuMapper.class);
        userRoleMapper = mock(PermUserRoleMapper.class);
        roleMapper = mock(PermRoleMapper.class);
        service = new MenuServiceImpl(menuMapper, roleMenuMapper, userRoleMapper, roleMapper);
    }

    private MenuSaveRequest request(String code, Long parentId) {
        MenuSaveRequest request = new MenuSaveRequest();
        request.setMenuCode(code);
        request.setMenuName("菜单");
        request.setParentId(parentId);
        return request;
    }

    private PermMenu menu(long id, Long parentId) {
        PermMenu menu = new PermMenu();
        menu.setId(id);
        menu.setMenuCode("m" + id);
        menu.setMenuName("菜单" + id);
        menu.setParentId(parentId);
        menu.setSort(0);
        menu.setStatus(PermMenu.STATUS_ENABLED);
        return menu;
    }

    @Test
    void 新增菜单编码重复抛出业务异常() {
        when(menuMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.create(request("home", 0L)));
        assertEquals(ErrorCode.MENU_CODE_EXISTS.getCode(), ex.getCode());
        verify(menuMapper, never()).insert(any(PermMenu.class));
    }

    @Test
    void 新增菜单父菜单不存在抛出业务异常() {
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.create(request("home", 9L)));
        assertEquals(ErrorCode.MENU_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 新增根菜单成功() {
        when(menuMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.insert(any(PermMenu.class))).thenAnswer(invocation -> {
            PermMenu inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L));

        PermMenu result = service.create(request("home", 0L));

        assertEquals(1L, result.getId());
    }

    @Test
    void 删除有子菜单抛出业务异常() {
        when(menuMapper.selectById(1L)).thenReturn(menu(1L, 0L));
        when(menuMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.delete(1L));
        assertEquals(ErrorCode.MENU_HAS_CHILDREN.getCode(), ex.getCode());
    }

    @Test
    void 删除不存在菜单抛出业务异常() {
        when(menuMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete(9L));
        assertEquals(ErrorCode.MENU_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 菜单树按父子组装() {
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(1L, 0L), menu(2L, 1L)));

        List<MenuTreeNode> roots = service.tree();

        assertEquals(1, roots.size());
        assertEquals(1L, roots.get(0).getId());
        assertEquals(1, roots.get(0).getChildren().size());
        assertEquals(2L, roots.get(0).getChildren().get(0).getId());
    }

    @Test
    void 用户无角色时菜单树为空() {
        when(userRoleMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of());

        assertTrue(service.userMenuTree(1L).isEmpty());
    }

    @Test
    void 用户角色禁用时菜单树为空() {
        when(userRoleMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of(5L));
        when(roleMapper.selectList(any())).thenReturn(List.of());

        assertTrue(service.userMenuTree(1L).isEmpty());
    }

    @Test
    void 用户菜单树取启用角色绑定并集() {
        PermRole role = new PermRole();
        role.setId(5L);
        role.setStatus(PermRole.STATUS_ENABLED);
        when(userRoleMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of(5L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role));
        PermRoleMenu binding = new PermRoleMenu();
        binding.setRoleId(5L);
        binding.setMenuId(1L);
        PermRoleMenu binding2 = new PermRoleMenu();
        binding2.setRoleId(5L);
        binding2.setMenuId(2L);
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(binding, binding2));
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(1L, 0L), menu(2L, 1L)));

        List<MenuTreeNode> roots = service.userMenuTree(1L);

        assertEquals(1, roots.size());
        assertEquals(1, roots.get(0).getChildren().size());
        verify(menuMapper).selectList(any());
    }
}
