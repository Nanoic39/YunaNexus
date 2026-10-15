package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.PermissionSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.service.PermissionService;
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
 * 权限管理接口（管理后台，roadmap 6.4）。<p>
 *
 * 路径前缀 {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin/permissions")
public class PermissionController {

    @Resource
    private PermissionService permissionService;

    @GetMapping
    public Result<List<PermPermission>> list() {
        return Result.success(permissionService.list());
    }

    @GetMapping("/{id}")
    public Result<PermPermission> get(@PathVariable("id") long id) {
        return Result.success(permissionService.getById(id));
    }

    @PostMapping
    public Result<PermPermission> create(@Valid @RequestBody PermissionSaveRequest request) {
        return Result.success(permissionService.create(request));
    }

    @PostMapping("/{id}")
    public Result<PermPermission> update(@PathVariable("id") long id,
                                         @Valid @RequestBody PermissionSaveRequest request) {
        return Result.success(permissionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        permissionService.delete(id);
        return Result.success();
    }
}
