package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.permission.domain.DataScopeResponse;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.mapper.PermDeptClosureMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermRoleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.DataScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 数据范围解析服务实现。
 *
 * @author yunanexus
 */
@Service
public class DataScopeServiceImpl implements DataScopeService {

    private final PermUserRoleMapper userRoleMapper;
    private final PermRoleMapper roleMapper;
    private final PermUserDeptMapper userDeptMapper;
    private final PermRoleDeptMapper roleDeptMapper;
    private final PermDeptClosureMapper closureMapper;

    public DataScopeServiceImpl(PermUserRoleMapper userRoleMapper,
                                PermRoleMapper roleMapper,
                                PermUserDeptMapper userDeptMapper,
                                PermRoleDeptMapper roleDeptMapper,
                                PermDeptClosureMapper closureMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.userDeptMapper = userDeptMapper;
        this.roleDeptMapper = roleDeptMapper;
        this.closureMapper = closureMapper;
    }

    @Override
    public DataScopeResponse resolve(long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return DataScopeResponse.of(userId, false, List.of(), null);
        }
        List<PermRole> roles = roleMapper.selectList(new LambdaQueryWrapper<PermRole>()
                .in(PermRole::getId, roleIds)
                .eq(PermRole::getStatus, PermRole.STATUS_ENABLED));
        if (roles.isEmpty()) {
            return DataScopeResponse.of(userId, false, List.of(), null);
        }
        if (roles.stream().anyMatch(role -> scopeOf(role) == PermRole.DATA_SCOPE_ALL)) {
            return DataScopeResponse.of(userId, true, List.of(), null);
        }

        Long deptId = userDeptMapper.selectDeptIdByUserId(userId);
        Set<Long> deptIds = new LinkedHashSet<>();
        Long selfUserId = null;
        for (PermRole role : roles) {
            switch (scopeOf(role)) {
                case PermRole.DATA_SCOPE_DEPT -> {
                    if (deptId != null) {
                        deptIds.add(deptId);
                    }
                }
                case PermRole.DATA_SCOPE_DEPT_AND_CHILD -> {
                    if (deptId != null) {
                        deptIds.addAll(closureMapper.selectDescendantIds(deptId));
                    }
                }
                case PermRole.DATA_SCOPE_CUSTOM -> deptIds.addAll(roleDeptMapper.selectDeptIdsByRoleId(role.getId()));
                case PermRole.DATA_SCOPE_SELF -> selfUserId = userId;
                default -> {
                    // DATA_SCOPE_ALL 已在上方短路
                }
            }
        }
        return DataScopeResponse.of(userId, false, List.copyOf(deptIds), selfUserId);
    }

    private int scopeOf(PermRole role) {
        return role.getDataScope() == null ? PermRole.DATA_SCOPE_SELF : role.getDataScope();
    }
}
