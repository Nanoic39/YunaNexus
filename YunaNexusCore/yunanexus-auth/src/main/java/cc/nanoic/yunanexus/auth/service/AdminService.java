package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.entity.ApiEndpoint;
import cc.nanoic.yunanexus.auth.entity.ResourceEntity;
import cc.nanoic.yunanexus.auth.entity.Roles;
import cc.nanoic.yunanexus.auth.entity.UserRoles;
import cc.nanoic.yunanexus.auth.mapper.ApiEndpointMapper;
import cc.nanoic.yunanexus.auth.mapper.ResourceMapper;
import cc.nanoic.yunanexus.auth.mapper.RolesMapper;
import cc.nanoic.yunanexus.auth.mapper.UserRolesMapper;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台统一 Service，涵盖角色/资源/端点 CRUD
 */
@Service
public class AdminService {

    @Resource
    private RolesMapper rolesMapper;

    @Resource
    private ResourceMapper resourceMapper;

    @Resource
    private ApiEndpointMapper apiEndpointMapper;

    @Resource
    private UserRolesMapper userRolesMapper;

    // ==================== 角色管理 ====================

    public List<Map<String, Object>> listRoles() {
        List<Roles> roles = rolesMapper.selectList(
                new LambdaQueryWrapper<Roles>().orderByDesc(Roles::getLevel));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Roles role : roles) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", role.getId());
            map.put("name", role.getName());
            map.put("level", role.getLevel());
            map.put("permissions", JSON.parseArray(role.getPermissions(), String.class));
            map.put("status", role.getStatus());
            map.put("createdAt", role.getCreatedAt() != null ? role.getCreatedAt().toString() : null);
            // 关联用户数
            long userCount = userRolesMapper.selectCount(
                    new LambdaQueryWrapper<UserRoles>()
                            .eq(UserRoles::getRoleId, role.getId())
                            .eq(UserRoles::getStatus, 1));
            map.put("userCount", userCount);
            result.add(map);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> createRole(String name, Integer level, List<String> permissions, Integer status) {
        Roles role = new Roles();
        role.setName(name);
        role.setLevel(level != null ? level : 1);
        role.setPermissions(JSON.toJSONString(permissions != null ? permissions : new ArrayList<>()));
        role.setStatus(status != null ? status : 1);
        role.setCreatedAt(LocalDateTime.now());
        rolesMapper.insert(role);

        Map<String, Object> result = new HashMap<>();
        result.put("id", role.getId());
        result.put("name", role.getName());
        return result;
    }

    @Transactional
    public void updateRole(Long roleId, String name, Integer level, List<String> permissions, Integer status) {
        Roles role = rolesMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(R.NOT_FOUND, "角色不存在");
        }
        if (name != null) role.setName(name);
        if (level != null) role.setLevel(level);
        if (permissions != null) role.setPermissions(JSON.toJSONString(permissions));
        if (status != null) role.setStatus(status);
        rolesMapper.updateById(role);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        Roles role = rolesMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(R.NOT_FOUND, "角色不存在");
        }
        // 解除该角色下所有用户的绑定
        userRolesMapper.update(null,
                new LambdaUpdateWrapper<UserRoles>()
                        .eq(UserRoles::getRoleId, roleId)
                        .set(UserRoles::getStatus, 2));
        rolesMapper.deleteById(roleId);
    }

    // ==================== 资源管理 ====================

    public List<ResourceEntity> listResources(Integer type) {
        LambdaQueryWrapper<ResourceEntity> wrapper = new LambdaQueryWrapper<ResourceEntity>()
                .orderByAsc(ResourceEntity::getParentId)
                .orderByAsc(ResourceEntity::getSortNo);
        if (type != null) {
            wrapper.eq(ResourceEntity::getType, type);
        }
        return resourceMapper.selectList(wrapper);
    }

    @Transactional
    public ResourceEntity createResource(Long parentId, String name, String code, Integer type,
            String icon, String path, String redirect, String component, Integer sortNo, Integer visible) {
        ResourceEntity r = new ResourceEntity();
        r.setParentId(parentId != null ? parentId : 0L);
        r.setName(name);
        r.setCode(code);
        r.setType(type != null ? type : 0);
        r.setIcon(icon);
        r.setPath(path);
        r.setRedirect(redirect);
        r.setComponent(component);
        r.setSortNo(sortNo != null ? sortNo : 0);
        r.setVisible(visible != null ? visible : 1);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        resourceMapper.insert(r);
        return r;
    }

    @Transactional
    public void updateResource(Long id, Long parentId, String name, String code, Integer type,
            String icon, String path, String redirect, String component, Integer sortNo, Integer visible) {
        ResourceEntity r = resourceMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(R.NOT_FOUND, "资源不存在");
        }
        if (parentId != null) r.setParentId(parentId);
        if (name != null) r.setName(name);
        if (code != null) r.setCode(code);
        if (type != null) r.setType(type);
        if (icon != null) r.setIcon(icon);
        if (path != null) r.setPath(path);
        if (redirect != null) r.setRedirect(redirect);
        if (component != null) r.setComponent(component);
        if (sortNo != null) r.setSortNo(sortNo);
        if (visible != null) r.setVisible(visible);
        r.setUpdatedAt(LocalDateTime.now());
        resourceMapper.updateById(r);
    }

    @Transactional
    public void updateResourceSort(Long id, Integer sortNo) {
        ResourceEntity r = resourceMapper.selectById(id);
        if (r == null) {
            throw new BusinessException(R.NOT_FOUND, "资源不存在");
        }
        r.setSortNo(sortNo);
        r.setUpdatedAt(LocalDateTime.now());
        resourceMapper.updateById(r);
    }

    @Transactional
    public int deleteResource(Long id) {
        int count = countChildren(id);
        // 递归删除子资源
        List<ResourceEntity> children = resourceMapper.selectList(
                new LambdaQueryWrapper<ResourceEntity>().eq(ResourceEntity::getParentId, id));
        for (ResourceEntity child : children) {
            deleteResource(child.getId());
        }
        resourceMapper.deleteById(id);
        return count + 1; // 自身 + 所有子孙
    }

    private int countChildren(Long parentId) {
        List<ResourceEntity> children = resourceMapper.selectList(
                new LambdaQueryWrapper<ResourceEntity>().eq(ResourceEntity::getParentId, parentId));
        int count = children.size();
        for (ResourceEntity child : children) {
            count += countChildren(child.getId());
        }
        return count;
    }

    // ==================== 端点管理 ====================

    public List<ApiEndpoint> listEndpoints(String serviceName, String httpMethod, String pathPattern,
            Integer page, Integer size) {
        LambdaQueryWrapper<ApiEndpoint> wrapper = new LambdaQueryWrapper<>();
        if (serviceName != null && !serviceName.isEmpty()) {
            wrapper.eq(ApiEndpoint::getServiceName, serviceName);
        }
        if (httpMethod != null && !httpMethod.isEmpty()) {
            wrapper.eq(ApiEndpoint::getHttpMethod, httpMethod);
        }
        if (pathPattern != null && !pathPattern.isEmpty()) {
            wrapper.like(ApiEndpoint::getPathPattern, pathPattern);
        }
        wrapper.orderByAsc(ApiEndpoint::getServiceName)
                .orderByAsc(ApiEndpoint::getPathPattern);

        if (page == null) page = 1;
        if (size == null) size = 20;

        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + offset + "," + size);
        return apiEndpointMapper.selectList(wrapper);
    }

    public long countEndpoints(String serviceName, String httpMethod, String pathPattern) {
        LambdaQueryWrapper<ApiEndpoint> wrapper = new LambdaQueryWrapper<>();
        if (serviceName != null && !serviceName.isEmpty()) {
            wrapper.eq(ApiEndpoint::getServiceName, serviceName);
        }
        if (httpMethod != null && !httpMethod.isEmpty()) {
            wrapper.eq(ApiEndpoint::getHttpMethod, httpMethod);
        }
        if (pathPattern != null && !pathPattern.isEmpty()) {
            wrapper.like(ApiEndpoint::getPathPattern, pathPattern);
        }
        return apiEndpointMapper.selectCount(wrapper);
    }

    @Transactional
    public ApiEndpoint createEndpoint(String serviceName, String httpMethod, String pathPattern,
            String requiredCode, String description) {
        ApiEndpoint ep = new ApiEndpoint();
        ep.setServiceName(serviceName);
        ep.setHttpMethod(httpMethod);
        ep.setPathPattern(pathPattern);
        ep.setRequiredCode(requiredCode);
        ep.setDescription(description);
        ep.setSource(1); // 管理端手动创建
        ep.setStatus(1);
        ep.setCreatedAt(LocalDateTime.now());
        ep.setUpdatedAt(LocalDateTime.now());
        apiEndpointMapper.insert(ep);
        return ep;
    }

    @Transactional
    public void updateEndpoint(Long id, String serviceName, String httpMethod, String pathPattern,
            String requiredCode, String description) {
        ApiEndpoint ep = apiEndpointMapper.selectById(id);
        if (ep == null) {
            throw new BusinessException(R.NOT_FOUND, "端点不存在");
        }
        if (serviceName != null) ep.setServiceName(serviceName);
        if (httpMethod != null) ep.setHttpMethod(httpMethod);
        if (pathPattern != null) ep.setPathPattern(pathPattern);
        if (requiredCode != null) ep.setRequiredCode(requiredCode);
        if (description != null) ep.setDescription(description);
        ep.setUpdatedAt(LocalDateTime.now());
        apiEndpointMapper.updateById(ep);
    }

    @Transactional
    public void toggleEndpoint(Long id) {
        ApiEndpoint ep = apiEndpointMapper.selectById(id);
        if (ep == null) {
            throw new BusinessException(R.NOT_FOUND, "端点不存在");
        }
        ep.setStatus(ep.getStatus() == 1 ? 0 : 1);
        ep.setUpdatedAt(LocalDateTime.now());
        apiEndpointMapper.updateById(ep);
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        apiEndpointMapper.deleteById(id);
    }
}
