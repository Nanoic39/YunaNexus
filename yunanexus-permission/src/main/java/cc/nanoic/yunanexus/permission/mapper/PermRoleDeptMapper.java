package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermRoleDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-自定义数据范围部门 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermRoleDeptMapper extends BaseMapper<PermRoleDept> {

    /**
     * 查询角色的自定义数据范围部门 ID。
     *
     * @param roleId 角色 ID
     * @return 部门 ID 列表
     */
    @Select("SELECT dept_id FROM perm_role_dept WHERE role_id = #{roleId}")
    List<Long> selectDeptIdsByRoleId(@Param("roleId") long roleId);
}
