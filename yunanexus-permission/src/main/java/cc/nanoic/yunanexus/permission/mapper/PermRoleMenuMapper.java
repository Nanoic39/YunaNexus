package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermRoleMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色-菜单绑定 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermRoleMenuMapper extends BaseMapper<PermRoleMenu> {

    /**
     * 查询角色的菜单 ID 集合。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    @Select("SELECT menu_id FROM perm_role_menu WHERE role_id = #{roleId}")
    List<Long> selectMenuIdsByRoleId(@Param("roleId") long roleId);
}
