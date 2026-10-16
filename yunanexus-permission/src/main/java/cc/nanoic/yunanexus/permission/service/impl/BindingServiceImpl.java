package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.entity.PermDept;
import cc.nanoic.yunanexus.permission.entity.PermMenu;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.entity.PermRoleDept;
import cc.nanoic.yunanexus.permission.entity.PermRoleMenu;
import cc.nanoic.yunanexus.permission.entity.PermRolePermission;
import cc.nanoic.yunanexus.permission.entity.PermUserDept;
import cc.nanoic.yunanexus.permission.entity.PermUserRole;
import cc.nanoic.yunanexus.permission.mapper.PermDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMenuMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRolePermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.BindingService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 绑定关系服务实现。
 *
 * <p>采用覆盖语义：以本次提交的集合整体替换现有绑定，天然幂等。
 *
 * @author yunanexus
 */
@Service
public class BindingServiceImpl implements BindingService {
    @org.springframework.beans.factory.annotation.Autowired
    private cc.nanoic.yunanexus.permission.security.PrivilegedRoleGuard privileged;

    @org.springframework.beans.factory.annotation.Autowired
    private cc.nanoic.yunanexus.permission.security.RoleDelegationGuard delegation;

    private final PermUserRoleMapper userRoleMapper;
    private final PermRolePermissionMapper rolePermissionMapper;
    private final PermRoleMapper roleMapper;
    private final PermPermissionMapper permissionMapper;
    private final PermUserDeptMapper userDeptMapper;
    private final PermRoleDeptMapper roleDeptMapper;
    private final PermDeptMapper deptMapper;
    private final PermRoleMenuMapper roleMenuMapper;
    private final PermMenuMapper menuMapper;

    public BindingServiceImpl(
            PermUserRoleMapper userRoleMapper,
            PermRolePermissionMapper rolePermissionMapper,
            PermRoleMapper roleMapper,
            PermPermissionMapper permissionMapper,
            PermUserDeptMapper userDeptMapper,
            PermRoleDeptMapper roleDeptMapper,
            PermDeptMapper deptMapper,
            PermRoleMenuMapper roleMenuMapper,
            PermMenuMapper menuMapper) {
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userDeptMapper = userDeptMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.deptMapper = deptMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    @Override
    public List<Long> listRoleIdsByUserId(long userId) {
        return userRoleMapper.selectRoleIdsByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setUserRoles(long userId, List<Long> roleIds) {
        delegation.assignment(userId, roleIds);
        privileged.assignment(userId, roleIds);
        List<Long> targets = distinct(roleIds);
        if (!targets.isEmpty()) {
            long found =
                    roleMapper.selectCount(
                            new LambdaQueryWrapper<PermRole>().in(PermRole::getId, targets));
            if (found != targets.size()) {
                throw new BizException(ErrorCode.ROLE_NOT_FOUND);
            }
        }
        userRoleMapper.delete(
                new LambdaQueryWrapper<PermUserRole>().eq(PermUserRole::getUserId, userId));
        for (Long roleId : targets) {
            PermUserRole userRole = new PermUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    @Override
    public List<Long> listPermissionIdsByRoleId(long roleId) {
        return rolePermissionMapper.selectPermissionIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setRolePermissions(long roleId, List<Long> permissionIds) {
        delegation.permissions(roleId, permissionIds);
        privileged.role(roleId);
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        List<Long> targets = distinct(permissionIds);
        if (!targets.isEmpty()) {
            long found =
                    permissionMapper.selectCount(
                            new LambdaQueryWrapper<PermPermission>()
                                    .in(PermPermission::getId, targets));
            if (found != targets.size()) {
                throw new BizException(ErrorCode.PERMISSION_NOT_FOUND);
            }
        }
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<PermRolePermission>()
                        .eq(PermRolePermission::getRoleId, roleId));
        for (Long permissionId : targets) {
            PermRolePermission rolePermission = new PermRolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissionMapper.insert(rolePermission);
        }
    }

    @Override
    public Long getUserDeptId(long userId) {
        return userDeptMapper.selectDeptIdByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setUserDept(long userId, Long deptId) {
        userDeptMapper.delete(
                new LambdaQueryWrapper<PermUserDept>().eq(PermUserDept::getUserId, userId));
        if (deptId == null) {
            return;
        }
        if (deptMapper.selectById(deptId) == null) {
            throw new BizException(ErrorCode.DEPT_NOT_FOUND);
        }
        PermUserDept userDept = new PermUserDept();
        userDept.setUserId(userId);
        userDept.setDeptId(deptId);
        userDeptMapper.insert(userDept);
    }

    @Override
    public List<Long> listRoleDeptIds(long roleId) {
        return roleDeptMapper.selectDeptIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setRoleDepts(long roleId, List<Long> deptIds) {
        delegation.manage(roleId);
        delegation.departments(deptIds);
        privileged.role(roleId);
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        List<Long> targets = distinct(deptIds);
        if (!targets.isEmpty()) {
            long found =
                    deptMapper.selectCount(
                            new LambdaQueryWrapper<PermDept>().in(PermDept::getId, targets));
            if (found != targets.size()) {
                throw new BizException(ErrorCode.DEPT_NOT_FOUND);
            }
        }
        roleDeptMapper.delete(
                new LambdaQueryWrapper<PermRoleDept>().eq(PermRoleDept::getRoleId, roleId));
        for (Long deptId : targets) {
            PermRoleDept roleDept = new PermRoleDept();
            roleDept.setRoleId(roleId);
            roleDept.setDeptId(deptId);
            roleDeptMapper.insert(roleDept);
        }
    }

    @Override
    public List<Long> listMenuIdsByRoleId(long roleId) {
        return roleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setRoleMenus(long roleId, List<Long> menuIds) {
        delegation.menus(roleId, menuIds);
        privileged.role(roleId);
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        List<Long> targets = distinct(menuIds);
        if (!targets.isEmpty()) {
            long found =
                    menuMapper.selectCount(
                            new LambdaQueryWrapper<PermMenu>().in(PermMenu::getId, targets));
            if (found != targets.size()) {
                throw new BizException(ErrorCode.MENU_NOT_FOUND);
            }
        }
        roleMenuMapper.delete(
                new LambdaQueryWrapper<PermRoleMenu>().eq(PermRoleMenu::getRoleId, roleId));
        for (Long menuId : targets) {
            PermRoleMenu roleMenu = new PermRoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
    }

    private List<Long> distinct(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
