package cc.nanoic.yunanexus.permission.security;

import cc.nanoic.yunanexus.permission.entity.PermRoleMenu;
import cc.nanoic.yunanexus.permission.entity.PermRolePermission;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRolePermissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

/** Shared catalog changes must not bypass the protection on their bound roles. */
@Component
public class CatalogMutationGuard {
    private final PermRolePermissionMapper permissions;
    private final PermRoleMenuMapper menus;
    private final PrivilegedRoleGuard privileged;
    private final RoleDelegationGuard delegation;

    public CatalogMutationGuard(PermRolePermissionMapper permissions, PermRoleMenuMapper menus,
            PrivilegedRoleGuard privileged, RoleDelegationGuard delegation) {
        this.permissions = permissions;
        this.menus = menus;
        this.privileged = privileged;
        this.delegation = delegation;
    }

    public void permission(long id) {
        permissions.selectList(new LambdaQueryWrapper<PermRolePermission>()
                .eq(PermRolePermission::getPermissionId, id)).stream()
                .map(PermRolePermission::getRoleId).distinct().forEach(this::role);
    }

    public void menu(long id) {
        menus.selectList(new LambdaQueryWrapper<PermRoleMenu>()
                .eq(PermRoleMenu::getMenuId, id)).stream()
                .map(PermRoleMenu::getRoleId).distinct().forEach(this::role);
    }

    private void role(long id) {
        privileged.role(id);
        delegation.manage(id);
    }
}
