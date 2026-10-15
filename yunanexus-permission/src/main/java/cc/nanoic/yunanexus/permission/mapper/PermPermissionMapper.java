package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermPermissionMapper extends BaseMapper<PermPermission> {

    /**
     * 查询用户的有效权限集合（用户 → 启用角色 → 角色权限 → 启用权限）。<p>
     *
     * 用于 RBAC 鉴权判定：返回的每条记录以 {@code resource} + {@code action} 表示一项授权。
     *
     * @param userId 全局用户 ID
     * @return 有效权限（去重）
     */
    @Select("""
            SELECT DISTINCT p.id, p.perm_code, p.resource, p.action, p.description, p.status,
                            p.create_time, p.update_time
            FROM perm_user_role ur
            JOIN perm_role r ON r.id = ur.role_id AND r.status = 1
            JOIN perm_role_permission rp ON rp.role_id = ur.role_id
            JOIN perm_permission p ON p.id = rp.permission_id AND p.status = 1
            WHERE ur.user_id = #{userId}
            """)
    List<PermPermission> selectEffectiveByUserId(@Param("userId") long userId);
}
