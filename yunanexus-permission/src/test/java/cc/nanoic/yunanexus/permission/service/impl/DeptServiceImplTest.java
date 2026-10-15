package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.DeptSaveRequest;
import cc.nanoic.yunanexus.permission.domain.DeptTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermDept;
import cc.nanoic.yunanexus.permission.mapper.PermDeptClosureMapper;
import cc.nanoic.yunanexus.permission.mapper.PermDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeptServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class DeptServiceImplTest {

    private PermDeptMapper deptMapper;
    private PermDeptClosureMapper closureMapper;
    private PermUserDeptMapper userDeptMapper;
    private PermRoleDeptMapper roleDeptMapper;
    private DeptServiceImpl service;

    @BeforeEach
    void setUp() {
        deptMapper = mock(PermDeptMapper.class);
        closureMapper = mock(PermDeptClosureMapper.class);
        userDeptMapper = mock(PermUserDeptMapper.class);
        roleDeptMapper = mock(PermRoleDeptMapper.class);
        service = new DeptServiceImpl(deptMapper, closureMapper, userDeptMapper, roleDeptMapper);
    }

    private DeptSaveRequest request(String code, Long parentId) {
        DeptSaveRequest request = new DeptSaveRequest();
        request.setDeptCode(code);
        request.setDeptName("部门");
        request.setParentId(parentId);
        return request;
    }

    private PermDept dept(long id, Long parentId) {
        PermDept dept = new PermDept();
        dept.setId(id);
        dept.setDeptCode("d" + id);
        dept.setDeptName("部门" + id);
        dept.setParentId(parentId);
        dept.setSort(0);
        dept.setStatus(PermDept.STATUS_ENABLED);
        return dept;
    }

    /** 模拟 MyBatis 回填自增主键。 */
    private void stubInsertAssignsId(long id) {
        when(deptMapper.insert(any(PermDept.class))).thenAnswer(invocation -> {
            PermDept inserted = invocation.getArgument(0);
            inserted.setId(id);
            return 1;
        });
    }

    @Test
    void 新增部门编码重复抛出业务异常() {
        when(deptMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.create(request("hr", 0L)));
        assertEquals(ErrorCode.DEPT_CODE_EXISTS.getCode(), ex.getCode());
        verify(deptMapper, never()).insert(any(PermDept.class));
    }

    @Test
    void 新增部门父部门不存在抛出业务异常() {
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.create(request("hr", 9L)));
        assertEquals(ErrorCode.DEPT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 新增根部门仅写自身闭包() {
        when(deptMapper.selectCount(any())).thenReturn(0L);
        stubInsertAssignsId(1L);
        when(deptMapper.selectById(any())).thenReturn(dept(1L, 0L));

        service.create(request("root", null));

        verify(closureMapper).insert(anyLong(), anyLong(), eq(0));
        verify(closureMapper, never()).attachAncestors(anyLong(), anyLong());
    }

    @Test
    void 新增子部门挂接祖先闭包() {
        when(deptMapper.selectCount(any())).thenReturn(0L);
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L));
        stubInsertAssignsId(2L);

        service.create(request("child", 1L));

        verify(closureMapper).insert(anyLong(), anyLong(), anyInt());
        verify(closureMapper).attachAncestors(anyLong(), eq(1L));
    }

    @Test
    void 更新部门不存在抛出业务异常() {
        when(deptMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.update(1L, request("hr", 0L)));
        assertEquals(ErrorCode.DEPT_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 删除存在子部门时拒绝() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L));
        when(deptMapper.selectCount(any())).thenReturn(2L);

        BizException ex = assertThrows(BizException.class, () -> service.delete(1L));
        assertEquals(ErrorCode.DEPT_HAS_CHILDREN.getCode(), ex.getCode());
        verify(deptMapper, never()).deleteById(anyLong());
    }

    @Test
    void 删除叶子部门清理绑定与闭包() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L));
        when(deptMapper.selectCount(any())).thenReturn(0L);

        service.delete(1L);

        verify(userDeptMapper).delete(any());
        verify(roleDeptMapper).delete(any());
        verify(closureMapper).deleteByDescendant(1L);
        verify(deptMapper).deleteById(1L);
    }

    @Test
    void 构建部门树() {
        when(deptMapper.selectList(any())).thenReturn(List.of(dept(1L, 0L), dept(2L, 1L), dept(3L, 1L)));

        List<DeptTreeNode> tree = service.tree();

        assertEquals(1, tree.size());
        assertEquals(1L, tree.get(0).getId());
        assertEquals(2, tree.get(0).getChildren().size());
    }

    @Test
    void 查询及以下部门委托闭包表() {
        when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L));
        when(closureMapper.selectDescendantIds(1L)).thenReturn(List.of(1L, 2L, 3L));

        assertEquals(List.of(1L, 2L, 3L), service.listSelfAndDescendantIds(1L));
        verify(closureMapper, times(1)).selectDescendantIds(1L);
    }
}
