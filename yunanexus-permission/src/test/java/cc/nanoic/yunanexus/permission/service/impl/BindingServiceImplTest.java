package cc.nanoic.yunanexus.permission.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.entity.PermRolePermission;
import cc.nanoic.yunanexus.permission.entity.PermUserRole;
import cc.nanoic.yunanexus.permission.mapper.PermDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRolePermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@link BindingServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class BindingServiceImplTest {

    private PermUserRoleMapper userRoleMapper;
    private PermRolePermissionMapper rolePermissionMapper;
    private PermRoleMapper roleMapper;
    private PermPermissionMapper permissionMapper;
    private PermUserDeptMapper userDeptMapper;
    private PermRoleDeptMapper roleDeptMapper;
    private PermDeptMapper deptMapper;
    private PermRoleMenuMapper roleMenuMapper;
    private PermMenuMapper menuMapper;
    private BindingServiceImpl service;

    @BeforeEach
    void setUp() {
        userRoleMapper = mock(PermUserRoleMapper.class);
        rolePermissionMapper = mock(PermRolePermissionMapper.class);
        roleMapper = mock(PermRoleMapper.class);
        permissionMapper = mock(PermPermissionMapper.class);
        userDeptMapper = mock(PermUserDeptMapper.class);
        roleDeptMapper = mock(PermRoleDeptMapper.class);
        deptMapper = mock(PermDeptMapper.class);
        roleMenuMapper = mock(PermRoleMenuMapper.class);
        menuMapper = mock(PermMenuMapper.class);
        service =
                new BindingServiceImpl(
                        userRoleMapper,
                        rolePermissionMapper,
                        roleMapper,
                        permissionMapper,
                        userDeptMapper,
                        roleDeptMapper,
                        deptMapper,
                        roleMenuMapper,
                        menuMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(
                service,
                "delegation",
                mock(cc.nanoic.yunanexus.permission.security.RoleDelegationGuard.class));
        org.springframework.test.util.ReflectionTestUtils.setField(
                service,
                "privileged",
                mock(cc.nanoic.yunanexus.permission.security.PrivilegedRoleGuard.class));
    }

    @Test
    void 设置用户角色覆盖并去重() {
        when(roleMapper.selectCount(any())).thenReturn(2L);

        service.setUserRoles(1001L, List.of(1L, 2L, 2L));

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, times(2)).insert(any(PermUserRole.class));
    }

    @Test
    void 设置用户角色存在无效角色时拒绝() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        BizException ex =
                assertThrows(
                        BizException.class, () -> service.setUserRoles(1001L, List.of(1L, 2L)));
        assertEquals(ErrorCode.ROLE_NOT_FOUND.getCode(), ex.getCode());
        verify(userRoleMapper, never()).insert(any(PermUserRole.class));
    }

    @Test
    void 设置空角色集合清空绑定() {
        service.setUserRoles(1001L, List.of());

        verify(userRoleMapper).delete(any());
        verify(userRoleMapper, never()).insert(any(PermUserRole.class));
    }

    @Test
    void 设置角色权限时角色不存在抛出业务异常() {
        when(roleMapper.selectById(1L)).thenReturn(null);

        BizException ex =
                assertThrows(
                        BizException.class, () -> service.setRolePermissions(1L, List.of(10L)));
        assertEquals(ErrorCode.ROLE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 设置角色权限存在无效权限时拒绝() {
        PermRole role = new PermRole();
        role.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(permissionMapper.selectCount(any())).thenReturn(1L);

        BizException ex =
                assertThrows(
                        BizException.class,
                        () -> service.setRolePermissions(1L, List.of(10L, 11L)));
        assertEquals(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ex.getCode());
        verify(rolePermissionMapper, never()).insert(any(PermRolePermission.class));
    }

    @Test
    void 设置角色权限成功覆盖() {
        PermRole role = new PermRole();
        role.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(permissionMapper.selectCount(any())).thenReturn(2L);

        service.setRolePermissions(1L, List.of(10L, 11L));

        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, times(2)).insert(any(PermRolePermission.class));
    }

    @Test
    void 查询委托给Mapper() {
        when(userRoleMapper.selectRoleIdsByUserId(1L)).thenReturn(List.of(1L, 2L));
        when(rolePermissionMapper.selectPermissionIdsByRoleId(1L)).thenReturn(List.of(10L));

        assertEquals(List.of(1L, 2L), service.listRoleIdsByUserId(1L));
        assertEquals(List.of(10L), service.listPermissionIdsByRoleId(1L));
    }
}
