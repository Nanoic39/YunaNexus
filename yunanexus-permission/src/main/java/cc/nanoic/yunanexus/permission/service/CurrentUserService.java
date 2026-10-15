package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.CurrentUserView;

/**
 * 当前用户权限视图服务（对外 {@code GET /api/v1/permission/me}）。<p>
 *
 * 聚合 RBAC 有效态（启用角色 / 权限码 / 菜单树），供前端确定用户身份与做入口控制。
 *
 * @author yunanexus
 */
public interface CurrentUserService {

    /**
     * 加载当前用户的有效权限视图。
     *
     * @param userId 全局用户 ID
     * @return 角色、权限码并集与可见菜单树
     */
    CurrentUserView load(long userId);
}
