package cc.nanoic.yunanexus.permission.security;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;
import cc.nanoic.yunanexus.permission.entity.*;
import cc.nanoic.yunanexus.permission.mapper.*;
import cc.nanoic.yunanexus.permission.service.DataScopeService;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/** An operator may only delegate capabilities they currently hold. Never trust UI selections. */
@Component
public class RoleDelegationGuard {
    private final AdminBearerVerifier verifier;
    private final PermPermissionMapper permissions;
    private final PermRolePermissionMapper rolePermissions;
    private final PermRoleMapper roles;
    private final PermRoleDeptMapper roleDepts;
    private final PermRoleMenuMapper roleMenus;
    private final PermUserRoleMapper userRoles;
    private final DataScopeService scopes;

    public RoleDelegationGuard(
            AdminBearerVerifier v,
            PermPermissionMapper p,
            PermRolePermissionMapper rp,
            PermRoleMapper r,
            PermRoleDeptMapper rd,
            PermRoleMenuMapper rm,
            PermUserRoleMapper ur,
            DataScopeService s) {
        verifier = v;
        permissions = p;
        rolePermissions = rp;
        roles = r;
        roleDepts = rd;
        roleMenus = rm;
        userRoles = ur;
        scopes = s;
    }

    private AdminBearerVerifier.Actor actor() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes s))
            throw new BizException(ErrorCode.FORBIDDEN, "缺少管理员身份");
        String key = RoleDelegationGuard.class.getName() + ".actor";
        if (s.getRequest().getAttribute(key) instanceof AdminBearerVerifier.Actor cached)
            return cached;
        var actor = verifier.actor(s.getRequest().getHeader("Authorization"));
        if (actor == null) throw new BizException(ErrorCode.FORBIDDEN, "管理员身份校验失败");
        s.getRequest().setAttribute(key, actor);
        return actor;
    }

    private Set<Long> permissionIds(long uid) {
        var result = new HashSet<Long>();
        for (var p : permissions.selectEffectiveByUserId(uid)) result.add(p.getId());
        return result;
    }

    private void subset(Collection<Long> targets, Collection<Long> allowed, String message) {
        if (targets != null && !new HashSet<>(allowed).containsAll(targets))
            throw new BizException(ErrorCode.FORBIDDEN, message);
    }

    public void manage(long roleId) {
        var a = actor();
        if (a.superAdmin()) return;
        var role = roles.selectById(roleId);
        if (role == null) throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        subset(
                rolePermissions.selectPermissionIdsByRoleId(roleId),
                permissionIds(a.userId()),
                "不能编辑或分配权限高于自己的角色");
        scope(role.getDataScope());
        if (Objects.equals(role.getDataScope(), PermRole.DATA_SCOPE_CUSTOM))
            departments(roleDepts.selectDeptIdsByRoleId(roleId));
    }

    public void scope(Integer scope) {
        var a = actor();
        if (a.superAdmin() || scope == null || scope == PermRole.DATA_SCOPE_SELF) return;
        var owned = scopes.resolve(a.userId());
        if (owned.isAll()) return;
        if (scope != PermRole.DATA_SCOPE_CUSTOM)
            throw new BizException(ErrorCode.FORBIDDEN, "数据范围不能超出本人授权；请选择仅本人或已授权的自定义部门");
    }

    public void permissions(long roleId, List<Long> targets) {
        manage(roleId);
        var a = actor();
        if (!a.superAdmin()) subset(targets, permissionIds(a.userId()), "不能授予自己未持有的权限");
    }

    public void departments(List<Long> targets) {
        var a = actor();
        if (a.superAdmin()) return;
        var scope = scopes.resolve(a.userId());
        if (!scope.isAll()) subset(targets, scope.getDeptIds(), "部门范围超出本人授权");
    }

    public void menus(long roleId, List<Long> targets) {
        manage(roleId);
        var a = actor();
        if (a.superAdmin()) return;
        Set<Long> allowed = new HashSet<>();
        for (long id : userRoles.selectRoleIdsByUserId(a.userId())) {
            var role = roles.selectById(id);
            if (role != null && Objects.equals(role.getStatus(), 1))
                allowed.addAll(roleMenus.selectMenuIdsByRoleId(id));
        }
        subset(targets, allowed, "不能授予自己未持有的菜单");
    }

    public void assignment(long userId, List<Long> targets) {
        var a = actor();
        if (a.superAdmin()) return;
        Set<Long> changed = new HashSet<>(userRoles.selectRoleIdsByUserId(userId));
        if (targets != null) changed.addAll(targets);
        changed.remove(null);
        for (long id : changed) manage(id);
    }

    public Map<String, Object> capabilities() {
        var a = actor();
        if (a.superAdmin())
            return Map.of(
                    "unrestricted",
                    true,
                    "permissionIds",
                    List.of(),
                    "dataScopes",
                    List.of(1, 2, 3, 4, 5));
        boolean all = scopes.resolve(a.userId()).isAll();
        return Map.of(
                "unrestricted",
                false,
                "permissionIds",
                permissionIds(a.userId()),
                "dataScopes",
                all ? List.of(1, 2, 3, 4, 5) : List.of(4, 5));
    }
}
