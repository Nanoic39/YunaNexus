package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.permission.domain.DataScopeResponse;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.mapper.PermDeptClosureMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DataScopeServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class DataScopeServiceImplTest {

    private static final long USER_ID = 100L;
    private static final long DEPT_ID = 7L;

    private PermUserRoleMapper userRoleMapper;
    private PermRoleMapper roleMapper;
    private PermUserDeptMapper userDeptMapper;
    private PermRoleDeptMapper roleDeptMapper;
    private PermDeptClosureMapper closureMapper;
    private DataScopeServiceImpl service;

    @BeforeEach
    void setUp() {
        userRoleMapper = mock(PermUserRoleMapper.class);
        roleMapper = mock(PermRoleMapper.class);
        userDeptMapper = mock(PermUserDeptMapper.class);
        roleDeptMapper = mock(PermRoleDeptMapper.class);
        closureMapper = mock(PermDeptClosureMapper.class);
        service = new DataScopeServiceImpl(userRoleMapper, roleMapper, userDeptMapper, roleDeptMapper, closureMapper);
    }

    private PermRole role(long id, int dataScope) {
        PermRole role = new PermRole();
        role.setId(id);
        role.setDataScope(dataScope);
        role.setStatus(PermRole.STATUS_ENABLED);
        return role;
    }

    @Test
    void 无角色时无可见数据() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of());

        DataScopeResponse scope = service.resolve(USER_ID);

        assertFalse(scope.isAll());
        assertTrue(scope.getDeptIds().isEmpty());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void 角色均被禁用时无可见数据() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(1L));
        when(roleMapper.selectList(any())).thenReturn(List.of());

        DataScopeResponse scope = service.resolve(USER_ID);

        assertFalse(scope.isAll());
        assertTrue(scope.getDeptIds().isEmpty());
    }

    @Test
    void 任一角色全量则全量可见() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(1L, 2L));
        when(roleMapper.selectList(any())).thenReturn(List.of(
                role(1L, PermRole.DATA_SCOPE_SELF), role(2L, PermRole.DATA_SCOPE_ALL)));

        DataScopeResponse scope = service.resolve(USER_ID);

        assertTrue(scope.isAll());
    }

    @Test
    void 本部门范围() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(1L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, PermRole.DATA_SCOPE_DEPT)));
        when(userDeptMapper.selectDeptIdByUserId(USER_ID)).thenReturn(DEPT_ID);

        DataScopeResponse scope = service.resolve(USER_ID);

        assertEquals(List.of(DEPT_ID), scope.getDeptIds());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void 本部门及以下范围展开后代() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(1L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, PermRole.DATA_SCOPE_DEPT_AND_CHILD)));
        when(userDeptMapper.selectDeptIdByUserId(USER_ID)).thenReturn(DEPT_ID);
        when(closureMapper.selectDescendantIds(DEPT_ID)).thenReturn(List.of(DEPT_ID, 8L, 9L));

        DataScopeResponse scope = service.resolve(USER_ID);

        assertEquals(List.of(DEPT_ID, 8L, 9L), scope.getDeptIds());
    }

    @Test
    void 自定义部门范围取角色配置() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(1L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, PermRole.DATA_SCOPE_CUSTOM)));
        when(roleDeptMapper.selectDeptIdsByRoleId(1L)).thenReturn(List.of(3L, 4L));

        DataScopeResponse scope = service.resolve(USER_ID);

        assertEquals(List.of(3L, 4L), scope.getDeptIds());
    }

    @Test
    void 仅本人范围设置selfUserId() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(1L));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, PermRole.DATA_SCOPE_SELF)));

        DataScopeResponse scope = service.resolve(USER_ID);

        assertEquals(Long.valueOf(USER_ID), scope.getSelfUserId());
        assertTrue(scope.getDeptIds().isEmpty());
    }

    @Test
    void 多角色取并集() {
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(1L, 2L));
        when(roleMapper.selectList(any())).thenReturn(List.of(
                role(1L, PermRole.DATA_SCOPE_DEPT), role(2L, PermRole.DATA_SCOPE_CUSTOM)));
        when(userDeptMapper.selectDeptIdByUserId(USER_ID)).thenReturn(DEPT_ID);
        when(roleDeptMapper.selectDeptIdsByRoleId(2L)).thenReturn(List.of(30L));

        DataScopeResponse scope = service.resolve(USER_ID);

        assertEquals(List.of(DEPT_ID, 30L), scope.getDeptIds());
    }
}
