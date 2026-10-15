package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.MenuSaveRequest;
import cc.nanoic.yunanexus.permission.domain.MenuTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermMenu;

import java.util.List;

/**
 * 菜单 / 页面资源服务（Phase D）。
 *
 * @author yunanexus
 */
public interface MenuService {

    PermMenu create(MenuSaveRequest request);

    PermMenu update(long id, MenuSaveRequest request);

    void delete(long id);

    PermMenu getById(long id);

    List<PermMenu> list();

    /** 全量菜单树（管理后台）。 */
    List<MenuTreeNode> tree();

    /**
     * 用户在启用角色下可见的菜单树（内部接口）。
     *
     * @param userId 全局用户 ID
     * @return 菜单树
     */
    List<MenuTreeNode> userMenuTree(long userId);
}
