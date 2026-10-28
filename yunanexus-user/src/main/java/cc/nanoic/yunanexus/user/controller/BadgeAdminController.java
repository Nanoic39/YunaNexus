package cc.nanoic.yunanexus.user.controller;

import cc.nanoic.yunanexus.common.audit.annotation.AuditAction;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.user.service.BadgeCatalogService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user/admin/badges")
public class BadgeAdminController {
    private final BadgeCatalogService badges;

    public BadgeAdminController(BadgeCatalogService badges) {
        this.badges = badges;
    }

    @GetMapping
    public Result<?> list() {
        return Result.success(badges.list());
    }

    @PostMapping
    public Result<?> create(@RequestBody BadgeCatalogService.Save b) {
        return Result.success(badges.create(b));
    }

    @PostMapping("/{id}")
    public Result<?> update(@PathVariable("id") long id, @RequestBody BadgeCatalogService.Save b) {
        return Result.success(badges.update(id, b));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable("id") long id) {
        badges.remove(id);
        return Result.success();
    }

    public record Grant(List<Long> userIds) {}

    @AuditAction(module = "user", action = "GRANT_BADGE", risk = "HIGH")
    @PostMapping("/{id}/grant")
    public Result<?> grant(@PathVariable("id") long id, @RequestBody Grant b) {
        badges.grant(id, b.userIds());
        return Result.success();
    }

    @GetMapping("/records")
    public Result<?> records() {
        return Result.success(badges.records());
    }
}
