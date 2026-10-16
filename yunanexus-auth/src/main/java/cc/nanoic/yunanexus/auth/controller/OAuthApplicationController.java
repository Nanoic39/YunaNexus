package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.OAuthApplicationService;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class OAuthApplicationController {
    private final OAuthApplicationService apps;

    public OAuthApplicationController(OAuthApplicationService apps) {
        this.apps = apps;
    }

    private long owner(AuthenticatedIdentity i) {
        if (i == null || !"web".equals(i.clientCode())) throw new BizException(ErrorCode.FORBIDDEN);
        return i.userId();
    }

    @GetMapping("/api/v1/auth/account/oauth-apps")
    public Result<?> mine(@AuthenticationPrincipal AuthenticatedIdentity i) {
        return Result.success(apps.list(owner(i)));
    }

    @PostMapping("/api/v1/auth/account/oauth-apps")
    public Result<?> create(
            @AuthenticationPrincipal AuthenticatedIdentity i,
            @RequestBody OAuthApplicationService.Save b) {
        return Result.success(apps.create(owner(i), b));
    }

    @PostMapping("/api/v1/auth/account/oauth-apps/{id}")
    public Result<?> update(
            @AuthenticationPrincipal AuthenticatedIdentity i,
            @PathVariable("id") long id,
            @RequestBody OAuthApplicationService.Save b) {
        return Result.success(apps.update(owner(i), id, b));
    }

    @DeleteMapping("/api/v1/auth/account/oauth-apps/{id}")
    public Result<?> remove(
            @AuthenticationPrincipal AuthenticatedIdentity i, @PathVariable("id") long id) {
        apps.remove(id, owner(i));
        return Result.success();
    }

    @PostMapping("/api/v1/auth/account/oauth-apps/{id}/secret")
    public Result<?> secret(
            @AuthenticationPrincipal AuthenticatedIdentity i, @PathVariable("id") long id) {
        return Result.success(apps.reset(owner(i), id));
    }

    @GetMapping("/api/v1/auth/admin/oauth-apps")
    public Result<?> all() {
        return Result.success(apps.list(null));
    }

    public record Audit(boolean approved, String auditRemark) {}

    @PostMapping("/api/v1/auth/admin/oauth-apps/{id}/audit")
    public Result<?> audit(@PathVariable("id") long id, @RequestBody Audit b) {
        return Result.success(apps.audit(id, b.approved(), b.auditRemark()));
    }

    public record Status(int status) {}

    @PostMapping("/api/v1/auth/admin/oauth-apps/{id}/status")
    public Result<?> status(@PathVariable("id") long id, @RequestBody Status b) {
        apps.status(id, b.status());
        return Result.success();
    }

    @DeleteMapping("/api/v1/auth/admin/oauth-apps/{id}")
    public Result<?> removeAdmin(@PathVariable("id") long id) {
        apps.remove(id, null);
        return Result.success();
    }

    @GetMapping("/internal/auth/oauth/allow")
    public Result<Boolean> allow(
            @AuthenticationPrincipal AuthenticatedIdentity i,
            @RequestParam("method") String method,
            @RequestParam("path") String path) {
        return Result.success(
                i != null
                        && i.clientCode().startsWith("oauth_")
                        && apps.allows(i.clientCode(), method, path));
    }
}
