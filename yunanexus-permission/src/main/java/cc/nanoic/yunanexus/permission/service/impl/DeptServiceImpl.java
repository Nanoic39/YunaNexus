package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.DeptSaveRequest;
import cc.nanoic.yunanexus.permission.domain.DeptTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermDept;
import cc.nanoic.yunanexus.permission.entity.PermRoleDept;
import cc.nanoic.yunanexus.permission.entity.PermUserDept;
import cc.nanoic.yunanexus.permission.mapper.PermDeptClosureMapper;
import cc.nanoic.yunanexus.permission.mapper.PermDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.service.DeptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门服务实现。<p>
 *
 * 层级关系用闭包表（{@code perm_dept_closure}）维护：新建部门时写入自身（depth=0）
 * 并挂接父部门的全部祖先；「本部门及以下」查询退化为一次索引扫描。
 *
 * @author yunanexus
 */
@Service
public class DeptServiceImpl implements DeptService {

    private final PermDeptMapper deptMapper;
    private final PermDeptClosureMapper closureMapper;
    private final PermUserDeptMapper userDeptMapper;
    private final PermRoleDeptMapper roleDeptMapper;

    public DeptServiceImpl(PermDeptMapper deptMapper,
                           PermDeptClosureMapper closureMapper,
                           PermUserDeptMapper userDeptMapper,
                           PermRoleDeptMapper roleDeptMapper) {
        this.deptMapper = deptMapper;
        this.closureMapper = closureMapper;
        this.userDeptMapper = userDeptMapper;
        this.roleDeptMapper = roleDeptMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermDept create(DeptSaveRequest request) {
        if (deptMapper.selectCount(new LambdaQueryWrapper<PermDept>()
                .eq(PermDept::getDeptCode, request.getDeptCode())) > 0) {
            throw new BizException(ErrorCode.DEPT_CODE_EXISTS);
        }
        long parentId = request.getParentId() == null ? PermDept.ROOT_PARENT_ID : request.getParentId();
        if (parentId != PermDept.ROOT_PARENT_ID && deptMapper.selectById(parentId) == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }

        PermDept dept = new PermDept();
        dept.setDeptCode(request.getDeptCode());
        dept.setDeptName(request.getDeptName());
        dept.setParentId(parentId);
        dept.setSort(request.getSort() == null ? 0 : request.getSort());
        dept.setStatus(request.getStatus() == null ? PermDept.STATUS_ENABLED : request.getStatus());
        try {
            deptMapper.insert(dept);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DEPT_CODE_EXISTS);
        }

        closureMapper.insert(dept.getId(), dept.getId(), 0);
        if (parentId != PermDept.ROOT_PARENT_ID) {
            closureMapper.attachAncestors(dept.getId(), parentId);
        }
        return deptMapper.selectById(dept.getId());
    }

    @Override
    public PermDept update(long id, DeptSaveRequest request) {
        PermDept dept = requireDept(id);
        dept.setDeptName(request.getDeptName());
        if (request.getSort() != null) {
            dept.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            dept.setStatus(request.getStatus());
        }
        deptMapper.updateById(dept);
        return deptMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        requireDept(id);
        if (deptMapper.selectCount(new LambdaQueryWrapper<PermDept>()
                .eq(PermDept::getParentId, id)) > 0) {
            throw new BizException(ErrorCode.DEPT_HAS_CHILDREN);
        }
        userDeptMapper.delete(new LambdaQueryWrapper<PermUserDept>().eq(PermUserDept::getDeptId, id));
        roleDeptMapper.delete(new LambdaQueryWrapper<PermRoleDept>().eq(PermRoleDept::getDeptId, id));
        closureMapper.deleteByDescendant(id);
        deptMapper.deleteById(id);
    }

    @Override
    public PermDept getById(long id) {
        return requireDept(id);
    }

    @Override
    public List<PermDept> list() {
        return deptMapper.selectList(new LambdaQueryWrapper<PermDept>()
                .orderByAsc(PermDept::getSort)
                .orderByAsc(PermDept::getId));
    }

    @Override
    public List<DeptTreeNode> tree() {
        Map<Long, DeptTreeNode> nodes = new LinkedHashMap<>();
        for (PermDept dept : list()) {
            nodes.put(dept.getId(), DeptTreeNode.from(dept));
        }
        List<DeptTreeNode> roots = new ArrayList<>();
        for (DeptTreeNode node : nodes.values()) {
            DeptTreeNode parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public List<Long> listSelfAndDescendantIds(long deptId) {
        requireDept(deptId);
        return closureMapper.selectDescendantIds(deptId);
    }

    private PermDept requireDept(long id) {
        PermDept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        return dept;
    }
}
