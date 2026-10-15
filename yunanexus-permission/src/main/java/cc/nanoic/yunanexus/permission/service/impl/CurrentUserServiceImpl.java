package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.permission.domain.CurrentUserView;
import cc.nanoic.yunanexus.permission.domain.MenuTreeNode;
import cc.nanoic.yunanexus.permission.domain.RoleBrief;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.CurrentUserService;
import cc.nanoic.yunanexus.permission.service.MenuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 当前用户权限视图服务实现。<p>
 *
 * 复用既有 RBAC 读取路径：启用角色 = 用户绑定角色 ∩ 启用；权限码 = 用户 → 启用角色 →
 * 启用权限的并集；菜单树复用 {@link MenuService#userMenuTree(long)}。
 *
 * @author yunanexus
 */
@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    private final PermUserRoleMapper userRoleMapper;
    private final PermRoleMapper roleMapper;
    private final PermPermissionMapper permissionMapper;
    private final MenuService menuService;

    public CurrentUserServiceImpl(PermUserRoleMapper userRoleMapper,
                                  PermRoleMapper roleMapper,
                                  PermPermissionMapper permissionMapper,
                                  MenuService menuService) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.menuService = menuService;
    }

    @Override
    public CurrentUserView load(long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        List<RoleBrief> roles = roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectList(new LambdaQueryWrapper<PermRole>()
                        .in(PermRole::getId, roleIds)
                        .eq(PermRole::getStatus, PermRole.STATUS_ENABLED)
                        .orderByAsc(PermRole::getId))
                .stream().map(RoleBrief::from).toList();
        List<String> permissions = permissionMapper.selectEffectiveByUserId(userId)
                .stream()
                .map(PermPermission::getPermCode)
                .distinct()
                .sorted()
                .toList();
        List<MenuTreeNode> menus = menuService.userMenuTree(userId);
        return CurrentUserView.of(userId, roles, permissions, menus);
    }
}
