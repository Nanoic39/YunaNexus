package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.RoleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.service.RoleService;
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
 * 角色管理接口（管理后台，roadmap 6.3）。<p>
 *
 * 路径前缀 {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin/roles")
public class RoleController {

    @Resource
    private RoleService roleService;

    @GetMapping
    public Result<List<PermRole>> list() {
        return Result.success(roleService.list());
    }

    @GetMapping("/{id}")
    public Result<PermRole> get(@PathVariable("id") long id) {
        return Result.success(roleService.getById(id));
    }

    @PostMapping
    public Result<PermRole> create(@Valid @RequestBody RoleSaveRequest request) {
        return Result.success(roleService.create(request));
    }

    @PostMapping("/{id}")
    public Result<PermRole> update(@PathVariable("id") long id,
                                   @Valid @RequestBody RoleSaveRequest request) {
        return Result.success(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        roleService.delete(id);
        return Result.success();
    }
}
