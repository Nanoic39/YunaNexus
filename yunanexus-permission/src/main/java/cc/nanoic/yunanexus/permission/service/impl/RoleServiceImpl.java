package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.RoleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.entity.PermRoleDept;
import cc.nanoic.yunanexus.permission.entity.PermRolePermission;
import cc.nanoic.yunanexus.permission.entity.PermUserRole;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRolePermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理服务实现。
 *
 * @author yunanexus
 */
@Service
public class RoleServiceImpl implements RoleService {

    private final PermRoleMapper roleMapper;
    private final PermUserRoleMapper userRoleMapper;
    private final PermRolePermissionMapper rolePermissionMapper;
    private final PermRoleDeptMapper roleDeptMapper;

    public RoleServiceImpl(PermRoleMapper roleMapper,
                           PermUserRoleMapper userRoleMapper,
                           PermRolePermissionMapper rolePermissionMapper,
                           PermRoleDeptMapper roleDeptMapper) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleDeptMapper = roleDeptMapper;
    }

    @Override
    public PermRole create(RoleSaveRequest request) {
        if(java.util.Set.of("user","admin","super_admin").contains(request.getRoleCode()))throw new BizException(ErrorCode.FORBIDDEN,"保留角色不可创建");
        if (existsByCode(request.getRoleCode())) {
            throw new BizException(ErrorCode.ROLE_CODE_EXISTS);
        }
        PermRole role = new PermRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setDataScope(request.getDataScope() == null ? PermRole.DATA_SCOPE_SELF : request.getDataScope());
        role.setStatus(request.getStatus() == null ? PermRole.STATUS_ENABLED : request.getStatus());
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.ROLE_CODE_EXISTS);
        }
        return roleMapper.selectById(role.getId());
    }

    @Override
    public PermRole update(long id, RoleSaveRequest request) {
        PermRole role = requireRole(id);
        if(role.getRoleCode()!=null && java.util.Set.of("user","admin","super_admin").contains(role.getRoleCode()))throw new BizException(ErrorCode.FORBIDDEN,"系统默认角色不可修改或删除");
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        if (request.getDataScope() != null) {
            role.setDataScope(request.getDataScope());
        }
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        roleMapper.updateById(role);
        return roleMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        var role=requireRole(id);
        if(role.getRoleCode()!=null && java.util.Set.of("user","admin","super_admin").contains(role.getRoleCode()))throw new BizException(ErrorCode.FORBIDDEN,"系统默认角色不可删除");
        userRoleMapper.delete(new LambdaQueryWrapper<PermUserRole>().eq(PermUserRole::getRoleId, id));
        rolePermissionMapper.delete(new LambdaQueryWrapper<PermRolePermission>().eq(PermRolePermission::getRoleId, id));
        roleDeptMapper.delete(new LambdaQueryWrapper<PermRoleDept>().eq(PermRoleDept::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Override
    public PermRole getById(long id) {
        return requireRole(id);
    }

    @Override
    public List<PermRole> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<PermRole>().orderByAsc(PermRole::getId));
    }

    private boolean existsByCode(String roleCode) {
        return roleMapper.selectCount(new LambdaQueryWrapper<PermRole>()
                .eq(PermRole::getRoleCode, roleCode)) > 0;
    }

    private PermRole requireRole(long id) {
        PermRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }
}
