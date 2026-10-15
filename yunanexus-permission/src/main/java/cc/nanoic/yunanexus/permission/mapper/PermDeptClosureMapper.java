package cc.nanoic.yunanexus.permission.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门闭包表 Mapper（祖先-后代全路径，含自身 depth=0）。<p>
 *
 * 闭包表使「本部门及以下」查询退化为一次索引扫描，避免递归 CTE。
 *
 * @author yunanexus
 */
@Mapper
public interface PermDeptClosureMapper {

    /**
     * 插入一条闭包关系。
     *
     * @param ancestorId   祖先部门 ID
     * @param descendantId 后代部门 ID
     * @param depth        层级距离（0=自身）
     * @return 影响行数
     */
    @Insert("INSERT INTO perm_dept_closure (ancestor_id, descendant_id, depth) "
            + "VALUES (#{ancestorId}, #{descendantId}, #{depth})")
    int insert(@Param("ancestorId") long ancestorId,
               @Param("descendantId") long descendantId,
               @Param("depth") int depth);

    /**
     * 为新建部门挂接父部门的全部祖先（不含自身，自身由 {@link #insert} 单独写入）。
     *
     * @param deptId   新部门 ID
     * @param parentId 父部门 ID
     * @return 影响行数
     */
    @Insert("INSERT INTO perm_dept_closure (ancestor_id, descendant_id, depth) "
            + "SELECT ancestor_id, #{deptId}, depth + 1 FROM perm_dept_closure WHERE descendant_id = #{parentId}")
    int attachAncestors(@Param("deptId") long deptId, @Param("parentId") long parentId);

    /**
     * 查询部门的全部后代（含自身）。
     *
     * @param deptId 部门 ID
     * @return 部门 ID 列表（按 depth 升序）
     */
    @Select("SELECT descendant_id FROM perm_dept_closure WHERE ancestor_id = #{deptId} ORDER BY depth ASC")
    List<Long> selectDescendantIds(@Param("deptId") long deptId);

    /**
     * 删除部门自身的闭包关系（仅当无子部门时调用）。
     *
     * @param deptId 部门 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM perm_dept_closure WHERE descendant_id = #{deptId}")
    int deleteByDescendant(@Param("deptId") long deptId);
}
