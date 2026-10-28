package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.audit.annotation.AuditAction;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.DeptIdsRequest;
import cc.nanoic.yunanexus.permission.domain.MenuIdsRequest;
import cc.nanoic.yunanexus.permission.domain.PermissionIdsRequest;
import cc.nanoic.yunanexus.permission.domain.RoleIdsRequest;
import cc.nanoic.yunanexus.permission.domain.UserDeptRequest;
import cc.nanoic.yunanexus.permission.service.BindingService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 绑定关系接口（管理后台，roadmap 6.5 / 6.6）。<p>
 *
 * 采用覆盖语义：以提交的集合整体替换现有绑定。路径前缀
 * {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin")
public class BindingController {

    @Resource
    private BindingService bindingService;

    @GetMapping("/users/{userId}/roles")
    public Result<List<Long>> listUserRoles(@PathVariable("userId") long userId) {
        return Result.success(bindingService.listRoleIdsByUserId(userId));
    }

    @AuditAction(module = "permission", action = "ASSIGN_ROLE", risk = "CRITICAL")
    @PostMapping("/users/{userId}/roles")
    public Result<Void> setUserRoles(@PathVariable("userId") long userId,
                                     @Valid @RequestBody RoleIdsRequest request) {
        bindingService.setUserRoles(userId, request.getRoleIds());
        return Result.success();
    }

    @GetMapping("/roles/{roleId}/permissions")
    public Result<List<Long>> listRolePermissions(@PathVariable("roleId") long roleId) {
        return Result.success(bindingService.listPermissionIdsByRoleId(roleId));
    }

    @AuditAction(module = "permission", action = "ASSIGN_PERMISSION", risk = "CRITICAL")
    @PostMapping("/roles/{roleId}/permissions")
    public Result<Void> setRolePermissions(@PathVariable("roleId") long roleId,
                                           @Valid @RequestBody PermissionIdsRequest request) {
        bindingService.setRolePermissions(roleId, request.getPermissionIds());
        return Result.success();
    }

    @GetMapping("/users/{userId}/dept")
    public Result<Long> getUserDept(@PathVariable("userId") long userId) {
        return Result.success(bindingService.getUserDeptId(userId));
    }

    @AuditAction(module = "permission", action = "ASSIGN_DEPT", risk = "HIGH")
    @PostMapping("/users/{userId}/dept")
    public Result<Void> setUserDept(@PathVariable("userId") long userId,
                                    @Valid @RequestBody UserDeptRequest request) {
        bindingService.setUserDept(userId, request.getDeptId());
        return Result.success();
    }

    @DeleteMapping("/users/{userId}/dept")
    public Result<Void> removeUserDept(@PathVariable("userId") long userId) {
        bindingService.setUserDept(userId, null);
        return Result.success();
    }

    @GetMapping("/roles/{roleId}/depts")
    public Result<List<Long>> listRoleDepts(@PathVariable("roleId") long roleId) {
        return Result.success(bindingService.listRoleDeptIds(roleId));
    }

    @PostMapping("/roles/{roleId}/depts")
    public Result<Void> setRoleDepts(@PathVariable("roleId") long roleId,
                                     @Valid @RequestBody DeptIdsRequest request) {
        bindingService.setRoleDepts(roleId, request.getDeptIds());
        return Result.success();
    }

    @GetMapping("/roles/{roleId}/menus")
    public Result<List<Long>> listRoleMenus(@PathVariable("roleId") long roleId) {
        return Result.success(bindingService.listMenuIdsByRoleId(roleId));
    }

    @AuditAction(module = "permission", action = "ASSIGN_ROLE_MENU", risk = "CRITICAL")
    @PostMapping("/roles/{roleId}/menus")
    public Result<Void> setRoleMenus(@PathVariable("roleId") long roleId,
                                     @Valid @RequestBody MenuIdsRequest request) {
        bindingService.setRoleMenus(roleId, request.getMenuIds());
        return Result.success();
    }
}
