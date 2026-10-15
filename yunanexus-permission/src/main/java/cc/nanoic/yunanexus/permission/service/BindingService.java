package cc.nanoic.yunanexus.permission.service;

import java.util.List;

/**
 * 绑定关系服务（用户-角色、角色-权限）。
 *
 * @author yunanexus
 */
public interface BindingService {

    /** 查询用户已绑定的角色 ID。 */
    List<Long> listRoleIdsByUserId(long userId);

    /** 覆盖式设置用户角色集合。 */
    void setUserRoles(long userId, List<Long> roleIds);

    /** 查询角色已绑定的权限 ID。 */
    List<Long> listPermissionIdsByRoleId(long roleId);

    /** 覆盖式设置角色权限集合。 */
    void setRolePermissions(long roleId, List<Long> permissionIds);

    /** 查询用户所属部门 ID（未绑定返回 {@code null}）。 */
    Long getUserDeptId(long userId);

    /** 设置用户所属部门（{@code deptId} 为 {@code null} 表示解绑）。 */
    void setUserDept(long userId, Long deptId);

    /** 查询角色的自定义数据范围部门 ID。 */
    List<Long> listRoleDeptIds(long roleId);

    /** 覆盖式设置角色自定义数据范围部门。 */
    void setRoleDepts(long roleId, List<Long> deptIds);

    /** 查询角色已绑定的菜单 ID。 */
    List<Long> listMenuIdsByRoleId(long roleId);

    /** 覆盖式设置角色菜单集合。 */
    void setRoleMenus(long roleId, List<Long> menuIds);
}
