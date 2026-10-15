package cc.nanoic.yunanexus.permission.controller;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.permission.domain.MenuSaveRequest;
import cc.nanoic.yunanexus.permission.domain.MenuTreeNode;
import cc.nanoic.yunanexus.permission.entity.PermMenu;
import cc.nanoic.yunanexus.permission.service.MenuService;
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
 * 菜单 / 页面资源管理接口（管理后台，Phase D）。<p>
 *
 * 路径前缀 {@code /api/v1/permission/admin/**} 由 {@code AdminTokenFilter} 校验管理员密钥。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/permission/admin/menus")
public class MenuController {

    @Resource
    private MenuService menuService;

    @GetMapping
    public Result<List<PermMenu>> list() {
        return Result.success(menuService.list());
    }

    @GetMapping("/tree")
    public Result<List<MenuTreeNode>> tree() {
        return Result.success(menuService.tree());
    }

    @GetMapping("/{id}")
    public Result<PermMenu> get(@PathVariable("id") long id) {
        return Result.success(menuService.getById(id));
    }

    @PostMapping
    public Result<PermMenu> create(@Valid @RequestBody MenuSaveRequest request) {
        return Result.success(menuService.create(request));
    }

    @PostMapping("/{id}")
    public Result<PermMenu> update(@PathVariable("id") long id,
                                   @Valid @RequestBody MenuSaveRequest request) {
        return Result.success(menuService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") long id) {
        menuService.delete(id);
        return Result.success();
    }
}
