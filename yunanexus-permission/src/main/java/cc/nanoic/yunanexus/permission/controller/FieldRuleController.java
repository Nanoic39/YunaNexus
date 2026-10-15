package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.FieldRuleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermFieldRule;
import cc.nanoic.yunanexus.permission.service.FieldRuleService;
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
 * 字段级权限规则管理接口（管理后台，Phase C）。<p>
 *
 * 路径前缀 {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin/fields")
public class FieldRuleController {

    @Resource
    private FieldRuleService fieldRuleService;

    @GetMapping
    public Result<List<PermFieldRule>> list(@RequestParam(value = "subjectType", required = false) Integer subjectType,
                                            @RequestParam(value = "subjectId", required = false) Long subjectId,
                                            @RequestParam(value = "resourceType", required = false) String resourceType,
                                            @RequestParam(value = "fieldName", required = false) String fieldName,
                                            @RequestParam(value = "access", required = false) Integer access) {
        return Result.success(fieldRuleService.list(subjectType, subjectId, resourceType, fieldName, access));
    }

    @GetMapping("/{id}")
    public Result<PermFieldRule> get(@PathVariable("id") long id) {
        return Result.success(fieldRuleService.getById(id));
    }

    @PostMapping
    public Result<PermFieldRule> create(@Valid @RequestBody FieldRuleSaveRequest request) {
        return Result.success(fieldRuleService.create(request));
    }

    @PostMapping("/{id}")
    public Result<PermFieldRule> update(@PathVariable("id") long id,
                                        @Valid @RequestBody FieldRuleSaveRequest request) {
        return Result.success(fieldRuleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        fieldRuleService.delete(id);
        return Result.success();
    }
}
