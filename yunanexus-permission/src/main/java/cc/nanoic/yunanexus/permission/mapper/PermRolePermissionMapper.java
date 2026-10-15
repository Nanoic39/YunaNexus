package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermRolePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-权限绑定 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermRolePermissionMapper extends BaseMapper<PermRolePermission> {

    /**
     * 查询角色已绑定的权限 ID。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    @Select("SELECT permission_id FROM perm_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") long roleId);
}
