package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.DeptSaveRequest;
import cc.nanoic.yunanexus.permission.domain.DeptTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermDept;

import java.util.List;

/**
 * 部门（组织架构）服务。
 *
 * @author yunanexus
 */
public interface DeptService {

    /** 新增部门（维护闭包表）。 */
    PermDept create(DeptSaveRequest request);

    /** 更新部门（{@code deptCode} 与层级不可变，仅更新名称 / 排序 / 状态）。 */
    PermDept update(long id, DeptSaveRequest request);

    /** 删除部门（存在子部门时拒绝），并清理用户 / 角色绑定。 */
    void delete(long id);

    /** 按 ID 查询部门。 */
    PermDept getById(long id);

    /** 查询全部部门（按 sort、id 升序）。 */
    List<PermDept> list();

    /** 查询部门树。 */
    List<DeptTreeNode> tree();

    /** 查询部门自身及其全部后代 ID。 */
    List<Long> listSelfAndDescendantIds(long deptId);
}
