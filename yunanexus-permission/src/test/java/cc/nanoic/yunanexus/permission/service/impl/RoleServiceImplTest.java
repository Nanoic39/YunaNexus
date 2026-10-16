package cc.nanoic.yunanexus.permission.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.RoleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRolePermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RoleServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class RoleServiceImplTest {

    private PermRoleMapper roleMapper;
    private PermUserRoleMapper userRoleMapper;
    private PermRolePermissionMapper rolePermissionMapper;
    private PermRoleDeptMapper roleDeptMapper;
    private RoleServiceImpl service;

    @BeforeEach
    void setUp() {
        roleMapper = mock(PermRoleMapper.class);
        userRoleMapper = mock(PermUserRoleMapper.class);
        rolePermissionMapper = mock(PermRolePermissionMapper.class);
        roleDeptMapper = mock(PermRoleDeptMapper.class);
        service =
                new RoleServiceImpl(
                        roleMapper,
                        userRoleMapper,
                        rolePermissionMapper,
                        roleDeptMapper,
                        mock(cc.nanoic.yunanexus.permission.security.RoleDelegationGuard.class));
    }

    private RoleSaveRequest request(String code) {
        RoleSaveRequest request = new RoleSaveRequest();
        request.setRoleCode(code);
        request.setRoleName("名称");
        return request;
    }

    @Test
    void 新增角色标识重复抛出业务异常() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.create(request("editor")));
        assertEquals(ErrorCode.ROLE_CODE_EXISTS.getCode(), ex.getCode());
        verify(roleMapper, never()).insert(any(PermRole.class));
    }

    @Test
    void 新增角色默认启用并回查() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(PermRole.class))).thenReturn(1);
        PermRole persisted = new PermRole();
        persisted.setId(10L);
        persisted.setRoleCode("editor");
        persisted.setStatus(PermRole.STATUS_ENABLED);
        when(roleMapper.selectById(any())).thenReturn(persisted);

        PermRole result = service.create(request("editor"));

        assertEquals(10L, result.getId());
        assertEquals(PermRole.STATUS_ENABLED, result.getStatus());
    }

    @Test
    void 更新角色不存在抛出业务异常() {
        when(roleMapper.selectById(1L)).thenReturn(null);

        BizException ex =
                assertThrows(BizException.class, () -> service.update(1L, request("editor")));
        assertEquals(ErrorCode.ROLE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 更新角色仅改名称描述状态() {
        PermRole existing = new PermRole();
        existing.setId(1L);
        existing.setRoleCode("editor");
        existing.setRoleName("旧名");
        when(roleMapper.selectById(1L)).thenReturn(existing);
        when(roleMapper.updateById(any(PermRole.class))).thenReturn(1);

        RoleSaveRequest request = request("editor");
        request.setRoleName("新名");
        request.setStatus(PermRole.STATUS_DISABLED);
        service.update(1L, request);

        assertEquals("新名", existing.getRoleName());
        assertEquals(PermRole.STATUS_DISABLED, existing.getStatus());
        assertEquals("editor", existing.getRoleCode());
        verify(roleMapper).updateById(existing);
    }

    @Test
    void 删除角色级联清理绑定() {
        PermRole existing = new PermRole();
        existing.setId(1L);
        when(roleMapper.selectById(1L)).thenReturn(existing);

        service.delete(1L);

        verify(userRoleMapper).delete(any());
        verify(rolePermissionMapper).delete(any());
        verify(roleDeptMapper).delete(any());
        verify(roleMapper).deleteById(1L);
    }

    @Test
    void 查询角色不存在抛出业务异常() {
        when(roleMapper.selectById(anyLong())).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.getById(99L));
        assertEquals(ErrorCode.ROLE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 系统默认角色不可创建修改删除() {
        for (String code : java.util.List.of("admin", "super_admin", "user")) {
            assertEquals(
                    403,
                    assertThrows(BizException.class, () -> service.create(request(code)))
                            .getCode());
            var role = new PermRole();
            role.setId(99L);
            role.setRoleCode(code);
            when(roleMapper.selectById(99L)).thenReturn(role);
            assertEquals(
                    403,
                    assertThrows(BizException.class, () -> service.update(99L, request(code)))
                            .getCode());
            assertEquals(
                    403, assertThrows(BizException.class, () -> service.delete(99L)).getCode());
        }
    }
}
