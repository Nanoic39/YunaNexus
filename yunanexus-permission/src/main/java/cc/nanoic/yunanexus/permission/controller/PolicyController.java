package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.PolicySaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPolicy;
import cc.nanoic.yunanexus.permission.service.PolicyService;
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
 * ABAC 策略管理接口（管理后台，roadmap 6.7）。<p>
 *
 * 路径前缀 {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin/policies")
public class PolicyController {

    @Resource
    private PolicyService policyService;

    @GetMapping
    public Result<List<PermPolicy>> list() {
        return Result.success(policyService.list());
    }

    @GetMapping("/{id}")
    public Result<PermPolicy> get(@PathVariable("id") long id) {
        return Result.success(policyService.getById(id));
    }

    @PostMapping
    public Result<PermPolicy> create(@Valid @RequestBody PolicySaveRequest request) {
        return Result.success(policyService.create(request));
    }

    @PostMapping("/{id}")
    public Result<PermPolicy> update(@PathVariable("id") long id,
                                     @Valid @RequestBody PolicySaveRequest request) {
        return Result.success(policyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        policyService.delete(id);
        return Result.success();
    }
}
