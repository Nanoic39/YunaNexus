package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermRoleMapper extends BaseMapper<PermRole> {
    /** 仅启用的精确 admin 角色授予根管理能力，不受客户端角色缓存影响。 */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM perm_role r JOIN perm_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND BINARY r.role_code IN ('admin','super_admin') AND r.status=1")
    int countRootAdmin(@org.apache.ibatis.annotations.Param("userId") long userId);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM perm_role r JOIN perm_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} AND BINARY r.role_code='super_admin' AND r.status=1")
    int countSuperAdmin(@org.apache.ibatis.annotations.Param("userId") long userId);
}
