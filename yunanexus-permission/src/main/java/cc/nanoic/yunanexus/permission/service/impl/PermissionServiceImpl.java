package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.PermissionSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.entity.PermRolePermission;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRolePermissionMapper;
import cc.nanoic.yunanexus.permission.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 权限管理服务实现。
 *
 * @author yunanexus
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private final cc.nanoic.yunanexus.permission.security.CatalogMutationGuard catalog;

    private final PermPermissionMapper permissionMapper;
    private final PermRolePermissionMapper rolePermissionMapper;

    public PermissionServiceImpl(PermPermissionMapper permissionMapper,
                                 PermRolePermissionMapper rolePermissionMapper,
            cc.nanoic.yunanexus.permission.security.CatalogMutationGuard catalog) {
        this.catalog = catalog;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public PermPermission create(PermissionSaveRequest request) {
        if (existsByCode(request.getPermCode())) {
            throw new BizException(ErrorCode.PERMISSION_CODE_EXISTS);
        }
        PermPermission permission = new PermPermission();
        permission.setPermCode(request.getPermCode());
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        permission.setDescription(request.getDescription());
        permission.setStatus(request.getStatus() == null ? PermPermission.STATUS_ENABLED : request.getStatus());
        try {
            permissionMapper.insert(permission);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.PERMISSION_CODE_EXISTS);
        }
        return permissionMapper.selectById(permission.getId());
    }

    @Override
    public PermPermission update(long id, PermissionSaveRequest request) {
        catalog.permission(id);
        PermPermission permission = requirePermission(id);
        permission.setResource(request.getResource());
        permission.setAction(request.getAction());
        permission.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            permission.setStatus(request.getStatus());
        }
        permissionMapper.updateById(permission);
        return permissionMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long id) {
        catalog.permission(id);
        requirePermission(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<PermRolePermission>()
                .eq(PermRolePermission::getPermissionId, id));
        permissionMapper.deleteById(id);
    }

    @Override
    public PermPermission getById(long id) {
        return requirePermission(id);
    }

    @Override
    public List<PermPermission> list() {
        return permissionMapper.selectList(new LambdaQueryWrapper<PermPermission>()
                .orderByAsc(PermPermission::getId));
    }

    private boolean existsByCode(String permCode) {
        return permissionMapper.selectCount(new LambdaQueryWrapper<PermPermission>()
                .eq(PermPermission::getPermCode, permCode)) > 0;
    }

    private PermPermission requirePermission(long id) {
        PermPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BizException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        return permission;
    }
}
