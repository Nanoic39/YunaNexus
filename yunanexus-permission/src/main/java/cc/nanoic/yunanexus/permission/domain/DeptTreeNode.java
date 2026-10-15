package cc.nanoic.yunanexus.permission.domain;

import cc.nanoic.yunanexus.permission.entity.PermDept;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点。
 *
 * @author yunanexus
 */
@Data
public class DeptTreeNode {

    private Long id;
    private String deptCode;
    private String deptName;
    private Long parentId;
    private Integer sort;
    private Integer status;
    private List<DeptTreeNode> children = new ArrayList<>();

    public static DeptTreeNode from(PermDept dept) {
        DeptTreeNode node = new DeptTreeNode();
        node.setId(dept.getId());
        node.setDeptCode(dept.getDeptCode());
        node.setDeptName(dept.getDeptName());
        node.setParentId(dept.getParentId());
        node.setSort(dept.getSort());
        node.setStatus(dept.getStatus());
        return node;
    }
}
