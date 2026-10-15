package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.PermissionSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRolePermissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class PermissionServiceImplTest {

    private PermPermissionMapper permissionMapper;
    private PermRolePermissionMapper rolePermissionMapper;
    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        permissionMapper = mock(PermPermissionMapper.class);
        rolePermissionMapper = mock(PermRolePermissionMapper.class);
        service = new PermissionServiceImpl(permissionMapper, rolePermissionMapper);
    }

    private PermissionSaveRequest request(String code) {
        PermissionSaveRequest request = new PermissionSaveRequest();
        request.setPermCode(code);
        request.setResource("/api/v1/file/**");
        request.setAction("write");
        return request;
    }

    @Test
    void 新增权限标识重复抛出业务异常() {
        when(permissionMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.create(request("file:write")));
        assertEquals(ErrorCode.PERMISSION_CODE_EXISTS.getCode(), ex.getCode());
        verify(permissionMapper, never()).insert(any(PermPermission.class));
    }

    @Test
    void 新增权限默认启用并回查() {
        when(permissionMapper.selectCount(any())).thenReturn(0L);
        when(permissionMapper.insert(any(PermPermission.class))).thenReturn(1);
        PermPermission persisted = new PermPermission();
        persisted.setId(5L);
        persisted.setStatus(PermPermission.STATUS_ENABLED);
        when(permissionMapper.selectById(any())).thenReturn(persisted);

        PermPermission result = service.create(request("file:write"));

        assertEquals(5L, result.getId());
        assertEquals(PermPermission.STATUS_ENABLED, result.getStatus());
    }

    @Test
    void 更新权限不存在抛出业务异常() {
        when(permissionMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.update(1L, request("file:write")));
        assertEquals(ErrorCode.PERMISSION_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 更新权限仅改资源动作描述状态() {
        PermPermission existing = new PermPermission();
        existing.setId(1L);
        existing.setPermCode("file:write");
        when(permissionMapper.selectById(1L)).thenReturn(existing);
        when(permissionMapper.updateById(any(PermPermission.class))).thenReturn(1);

        PermissionSaveRequest request = request("file:write");
        request.setAction("read");
        service.update(1L, request);

        assertEquals("read", existing.getAction());
        assertEquals("file:write", existing.getPermCode());
        verify(permissionMapper).updateById(existing);
    }

    @Test
    void 删除权限级联清理角色绑定() {
        PermPermission existing = new PermPermission();
        existing.setId(1L);
        when(permissionMapper.selectById(1L)).thenReturn(existing);

        service.delete(1L);

        verify(rolePermissionMapper).delete(any());
        verify(permissionMapper).deleteById(1L);
    }
}
