package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.ResourceGrantSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermResourceGrant;
import cc.nanoic.yunanexus.permission.service.ResourceGrantService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源实例级授权管理接口（管理后台，Phase B）。<p>
 *
 * 路径前缀 {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin/grants")
public class ResourceGrantController {

    @Resource
    private ResourceGrantService grantService;

    @GetMapping
    public Result<List<PermResourceGrant>> list(@RequestParam(value = "subjectType", required = false) Integer subjectType,
                                                @RequestParam(value = "subjectId", required = false) Long subjectId,
                                                @RequestParam(value = "resourceType", required = false) String resourceType,
                                                @RequestParam(value = "resourceId", required = false) String resourceId) {
        return Result.success(grantService.list(subjectType, subjectId, resourceType, resourceId));
    }

    @GetMapping("/{id}")
    public Result<PermResourceGrant> get(@PathVariable("id") long id) {
        return Result.success(grantService.getById(id));
    }

    @PostMapping
    public Result<PermResourceGrant> create(@Valid @RequestBody ResourceGrantSaveRequest request) {
        return Result.success(grantService.create(request));
    }

    @PostMapping("/{id}")
    public Result<PermResourceGrant> update(@PathVariable("id") long id,
                                            @Valid @RequestBody ResourceGrantSaveRequest request) {
        return Result.success(grantService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        grantService.delete(id);
        return Result.success();
    }
}
