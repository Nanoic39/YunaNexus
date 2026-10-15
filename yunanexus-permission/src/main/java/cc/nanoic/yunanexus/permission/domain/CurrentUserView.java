package cc.nanoic.yunanexus.permission.domain;

import lombok.Data;

import java.util.List;

/**
 * 当前登录用户的权限视图（对外接口 {@code GET /api/v1/permission/me}）。<p>
 *
 * 聚合 RBAC 有效态：启用角色清单、启用角色下的权限码并集、可见菜单树。
 * 供前端确定用户身份、渲染动态菜单并做管理端入口 / 路由守卫判定。
 *
 * @author yunanexus
 */
@Data
public class CurrentUserView {

    /** 全局用户 ID（网关注入的 {@code X-User-Id}） */
    private Long userId;

    /** 用户已绑定的启用角色 */
    private List<RoleBrief> roles;

    /** 启用角色下的有效权限码（去重、升序） */
    private List<String> permissions;

    /** 启用角色下可见的菜单树（L1 页面级） */
    private List<MenuTreeNode> menus;

    public static CurrentUserView of(long userId,
                                     List<RoleBrief> roles,
                                     List<String> permissions,
                                     List<MenuTreeNode> menus) {
        CurrentUserView view = new CurrentUserView();
        view.setUserId(userId);
        view.setRoles(roles == null ? List.of() : roles);
        view.setPermissions(permissions == null ? List.of() : permissions);
        view.setMenus(menus == null ? List.of() : menus);
        return view;
    }
}
