package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.AdminUserVO;
import cc.nanoic.yunanexus.auth.entity.ApiEndpoint;
import cc.nanoic.yunanexus.auth.entity.ResourceEntity;
import cc.nanoic.yunanexus.auth.service.AdminService;
import cc.nanoic.yunanexus.auth.service.AdminUserService;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台接口：角色、资源、端点的 CRUD
 */
@RestController
@RequestMapping("/admin")
@RequirePermission(anyOf = { "admin:*", "core:*:*:manage", "admin:system:roles:read" })
public class AdminController {

    @Resource
    private AdminService adminService;

    @Resource
    private AdminUserService adminUserService;

    // ==================== 角色管理 ====================

    @GetMapping("/roles")
    public Result<List<Map<String, Object>>> listRoles() {
        return Result.success(adminService.listRoles());
    }

    @PostMapping("/roles")
    @RequirePermission(anyOf = { "admin:system:roles:write", "admin:*" })
    public Result<Map<String, Object>> createRole(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer level = body.get("level") != null ? ((Number) body.get("level")).intValue() : null;
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) body.get("permissions");
        Integer status = body.get("status") != null ? ((Number) body.get("status")).intValue() : null;
        return Result.success(adminService.createRole(name, level, permissions, status));
    }

    @PutMapping("/roles/{roleId}")
    @RequirePermission(anyOf = { "admin:system:roles:write", "admin:*" })
    public Result<?> updateRole(@PathVariable Long roleId, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Integer level = body.get("level") != null ? ((Number) body.get("level")).intValue() : null;
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) body.get("permissions");
        Integer status = body.get("status") != null ? ((Number) body.get("status")).intValue() : null;
        adminService.updateRole(roleId, name, level, permissions, status);
        return Result.success(null);
    }

    @DeleteMapping("/roles/{roleId}")
    @RequirePermission(anyOf = { "admin:system:roles:write", "admin:*" })
    public Result<?> deleteRole(@PathVariable Long roleId) {
        adminService.deleteRole(roleId);
        return Result.success(null);
    }

    // ==================== 资源管理 ====================

    @GetMapping("/resources")
    public Result<List<ResourceEntity>> listResources(@RequestParam(required = false) Integer type) {
        return Result.success(adminService.listResources(type));
    }

    @PostMapping("/resources")
    @RequirePermission(anyOf = { "admin:system:resources:write", "admin:*" })
    public Result<ResourceEntity> createResource(@RequestBody Map<String, Object> body) {
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;
        String name = (String) body.get("name");
        String code = (String) body.get("code");
        Integer type = body.get("type") != null ? ((Number) body.get("type")).intValue() : null;
        String icon = (String) body.get("icon");
        String path = (String) body.get("path");
        String redirect = (String) body.get("redirect");
        String component = (String) body.get("component");
        Integer sortNo = body.get("sortNo") != null ? ((Number) body.get("sortNo")).intValue() : null;
        Integer visible = body.get("visible") != null ? ((Number) body.get("visible")).intValue() : null;
        return Result.success(adminService.createResource(parentId, name, code, type, icon, path, redirect, component, sortNo, visible));
    }

    @PutMapping("/resources/{resourceId}")
    @RequirePermission(anyOf = { "admin:system:resources:write", "admin:*" })
    public Result<?> updateResource(@PathVariable Long resourceId, @RequestBody Map<String, Object> body) {
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;
        String name = (String) body.get("name");
        String code = (String) body.get("code");
        Integer type = body.get("type") != null ? ((Number) body.get("type")).intValue() : null;
        String icon = (String) body.get("icon");
        String path = (String) body.get("path");
        String redirect = (String) body.get("redirect");
        String component = (String) body.get("component");
        Integer sortNo = body.get("sortNo") != null ? ((Number) body.get("sortNo")).intValue() : null;
        Integer visible = body.get("visible") != null ? ((Number) body.get("visible")).intValue() : null;
        adminService.updateResource(resourceId, parentId, name, code, type, icon, path, redirect, component, sortNo, visible);
        return Result.success(null);
    }

    @PutMapping("/resources/{resourceId}/sort")
    @RequirePermission(anyOf = { "admin:system:resources:write", "admin:*" })
    public Result<?> updateResourceSort(@PathVariable Long resourceId, @RequestParam Integer sortNo) {
        adminService.updateResourceSort(resourceId, sortNo);
        return Result.success(null);
    }

    @DeleteMapping("/resources/{resourceId}")
    @RequirePermission(anyOf = { "admin:system:resources:write", "admin:*" })
    public Result<Map<String, Object>> deleteResource(@PathVariable Long resourceId) {
        int deleted = adminService.deleteResource(resourceId);
        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deleted);
        return Result.success(result);
    }

    // ==================== 端点管理 ====================

    @GetMapping("/endpoints")
    public Result<Map<String, Object>> listEndpoints(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        List<ApiEndpoint> list = adminService.listEndpoints(service, method, path, page, size);
        long total = adminService.countEndpoints(service, method, path);
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    @PostMapping("/endpoints")
    @RequirePermission(anyOf = { "admin:system:endpoints:write", "admin:*" })
    public Result<ApiEndpoint> createEndpoint(@RequestBody Map<String, Object> body) {
        String serviceName = (String) body.get("serviceName");
        String httpMethod = (String) body.get("httpMethod");
        String pathPattern = (String) body.get("pathPattern");
        String requiredCode = (String) body.get("requiredCode");
        String description = (String) body.get("description");
        return Result.success(adminService.createEndpoint(serviceName, httpMethod, pathPattern, requiredCode, description));
    }

    @PutMapping("/endpoints/{endpointId}")
    @RequirePermission(anyOf = { "admin:system:endpoints:write", "admin:*" })
    public Result<?> updateEndpoint(@PathVariable Long endpointId, @RequestBody Map<String, Object> body) {
        String serviceName = (String) body.get("serviceName");
        String httpMethod = (String) body.get("httpMethod");
        String pathPattern = (String) body.get("pathPattern");
        String requiredCode = (String) body.get("requiredCode");
        String description = (String) body.get("description");
        adminService.updateEndpoint(endpointId, serviceName, httpMethod, pathPattern, requiredCode, description);
        return Result.success(null);
    }

    @PutMapping("/endpoints/{endpointId}/status")
    @RequirePermission(anyOf = { "admin:system:endpoints:write", "admin:*" })
    public Result<?> toggleEndpoint(@PathVariable Long endpointId) {
        adminService.toggleEndpoint(endpointId);
        return Result.success(null);
    }

    @DeleteMapping("/endpoints/{endpointId}")
    @RequirePermission(anyOf = { "admin:system:endpoints:write", "admin:*" })
    public Result<?> deleteEndpoint(@PathVariable Long endpointId) {
        adminService.deleteEndpoint(endpointId);
        return Result.success(null);
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    @RequirePermission(anyOf = { "admin:users:read", "admin:users:*", "admin:*" })
    public Result<Map<String, Object>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        List<AdminUserVO> list = adminUserService.listUsers(keyword, status, page, size);
        long total = adminUserService.countUsers(keyword, status);
        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    @PutMapping("/users/{globalId}/status")
    @RequirePermission(anyOf = { "admin:users:write", "admin:users:*", "admin:*" })
    public Result<?> updateUserStatus(@PathVariable String globalId, @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") != null ? ((Number) body.get("status")).intValue() : null;
        adminUserService.updateUserStatus(globalId, status);
        return Result.success(null);
    }

    @PutMapping("/users/{globalId}/roles")
    @RequirePermission(anyOf = { "admin:users:roles:assign", "admin:users:*", "admin:*" })
    public Result<?> assignUserRoles(@PathVariable String globalId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> roleIdsRaw = (List<Integer>) body.get("roleIds");
        List<Long> roleIds = roleIdsRaw.stream().map(Long::valueOf).toList();
        adminUserService.assignRoles(globalId, roleIds);
        return Result.success(null);
    }
}
