package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.DeptSaveRequest;
import cc.nanoic.yunanexus.permission.domain.DeptTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermDept;
import cc.nanoic.yunanexus.permission.service.DeptService;
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
 * 部门（组织架构）管理接口（管理后台，Phase A）。<p>
 *
 * 路径前缀 {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin/depts")
public class DeptController {

    @Resource
    private DeptService deptService;

    @GetMapping
    public Result<List<PermDept>> list() {
        return Result.success(deptService.list());
    }

    @GetMapping("/tree")
    public Result<List<DeptTreeNode>> tree() {
        return Result.success(deptService.tree());
    }

    @GetMapping("/{id}")
    public Result<PermDept> get(@PathVariable("id") long id) {
        return Result.success(deptService.getById(id));
    }

    @PostMapping
    public Result<PermDept> create(@Valid @RequestBody DeptSaveRequest request) {
        return Result.success(deptService.create(request));
    }

    @PostMapping("/{id}")
    public Result<PermDept> update(@PathVariable("id") long id,
                                   @Valid @RequestBody DeptSaveRequest request) {
        return Result.success(deptService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        deptService.delete(id);
        return Result.success();
    }
}
