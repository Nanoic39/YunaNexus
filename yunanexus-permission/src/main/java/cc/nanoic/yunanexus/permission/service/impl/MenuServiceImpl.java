package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.MenuSaveRequest;
import cc.nanoic.yunanexus.permission.domain.MenuTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermMenu;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.entity.PermRoleMenu;
import cc.nanoic.yunanexus.permission.mapper.PermMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.MenuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单 / 页面资源服务实现。<p>
 *
 * 菜单为多级树结构（无闭包表，层级浅、读多写少）；用户可见菜单 = 其启用角色绑定菜单的并集。
 *
 * @author yunanexus
 */
@Service
public class MenuServiceImpl implements MenuService {

    private final PermMenuMapper menuMapper;
    private final PermRoleMenuMapper roleMenuMapper;
    private final PermUserRoleMapper userRoleMapper;
    private final PermRoleMapper roleMapper;

    public MenuServiceImpl(PermMenuMapper menuMapper,
                           PermRoleMenuMapper roleMenuMapper,
                           PermUserRoleMapper userRoleMapper,
                           PermRoleMapper roleMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermMenu create(MenuSaveRequest request) {
        if (menuMapper.selectCount(new LambdaQueryWrapper<PermMenu>()
                .eq(PermMenu::getMenuCode, request.getMenuCode())) > 0) {
            throw new BizException(ErrorCode.MENU_CODE_EXISTS);
        }
        long parentId = request.getParentId() == null ? PermMenu.ROOT_PARENT_ID : request.getParentId();
        if (parentId != PermMenu.ROOT_PARENT_ID && menuMapper.selectById(parentId) == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);
        }
        PermMenu menu = new PermMenu();
        menu.setMenuCode(request.getMenuCode());
        menu.setMenuName(request.getMenuName());
        menu.setParentId(parentId);
        menu.setPath(request.getPath());
        menu.setSort(request.getSort() == null ? 0 : request.getSort());
        menu.setStatus(request.getStatus() == null ? PermMenu.STATUS_ENABLED : request.getStatus());
        try {
            menuMapper.insert(menu);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.MENU_CODE_EXISTS);
        }
        return menuMapper.selectById(menu.getId());
    }

    @Override
    public PermMenu update(long id, MenuSaveRequest request) {
        PermMenu menu = requireMenu(id);
        menu.setMenuName(request.getMenuName());
        menu.setPath(request.getPath());
        if (request.getSort() != null) {
            menu.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            menu.setStatus(request.getStatus());
        }
        menuMapper.updateById(menu);
        return menuMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        requireMenu(id);
        if (menuMapper.selectCount(new LambdaQueryWrapper<PermMenu>().eq(PermMenu::getParentId, id)) > 0) {
            throw new BizException(ErrorCode.MENU_HAS_CHILDREN);
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<PermRoleMenu>().eq(PermRoleMenu::getMenuId, id));
        menuMapper.deleteById(id);
    }

    @Override
    public PermMenu getById(long id) {
        return requireMenu(id);
    }

    @Override
    public List<PermMenu> list() {
        return menuMapper.selectList(new LambdaQueryWrapper<PermMenu>()
                .orderByAsc(PermMenu::getSort)
                .orderByAsc(PermMenu::getId));
    }

    @Override
    public List<MenuTreeNode> tree() {
        return buildTree(list());
    }

    @Override
    public List<MenuTreeNode> userMenuTree(long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> enabledRoleIds = roleMapper.selectList(new LambdaQueryWrapper<PermRole>()
                        .in(PermRole::getId, roleIds)
                        .eq(PermRole::getStatus, PermRole.STATUS_ENABLED))
                .stream().map(PermRole::getId).toList();
        if (enabledRoleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<PermRoleMenu>()
                        .in(PermRoleMenu::getRoleId, enabledRoleIds))
                .stream().map(PermRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<PermMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<PermMenu>()
                .in(PermMenu::getId, menuIds)
                .eq(PermMenu::getStatus, PermMenu.STATUS_ENABLED)
                .orderByAsc(PermMenu::getSort)
                .orderByAsc(PermMenu::getId));
        return buildTree(menus);
    }

    /** 由扁平列表按 parentId 组装为树；父节点不在集合内者视为根。 */
    private List<MenuTreeNode> buildTree(List<PermMenu> menus) {
        Map<Long, MenuTreeNode> nodes = new LinkedHashMap<>();
        for (PermMenu menu : menus) {
            nodes.put(menu.getId(), MenuTreeNode.from(menu));
        }
        List<MenuTreeNode> roots = new ArrayList<>();
        for (MenuTreeNode node : nodes.values()) {
            MenuTreeNode parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private PermMenu requireMenu(long id) {
        PermMenu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.MENU_NOT_FOUND);
        }
        return menu;
    }
}
