package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色绑定 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermUserRoleMapper extends BaseMapper<PermUserRole> {

    /**
     * 查询用户已绑定的角色 ID。
     *
     * @param userId 全局用户 ID
     * @return 角色 ID 列表
     */
    @Select("SELECT role_id FROM perm_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") long userId);
}
