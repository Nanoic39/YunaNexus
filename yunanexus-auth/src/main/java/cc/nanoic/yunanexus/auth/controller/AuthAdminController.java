package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.domain.AppealAuditRequest;
import cc.nanoic.yunanexus.auth.domain.AppealView;
import cc.nanoic.yunanexus.auth.domain.UserView;
import cc.nanoic.yunanexus.auth.service.AdminService;
import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证中心管理后台接口（roadmap 3.9）。
 *
 * <p>所有端点位于 {@code /api/v1/auth/admin/**}，由 {@code AdminAuthenticationFilter} 校验请求头 {@code
 * X-Admin-Token} 后放行，与终端用户 JWT 完全解耦。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
public class AuthAdminController {

    @Resource private AdminService adminService;

    // ---------- 申诉审核 ----------

    @GetMapping("/appeals")
    public Result<PageResult<AppealView>> listAppeals(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(adminService.listAppeals(status, page, size));
    }

    @GetMapping("/appeals/{id}")
    public Result<AppealView> getAppeal(@PathVariable("id") long id) {
        return Result.success(adminService.getAppeal(id));
    }

    @PostMapping("/appeals/{id}/approve")
    public Result<Void> approveAppeal(
            @PathVariable("id") long id,
            @Valid @RequestBody(required = false) AppealAuditRequest request) {
        AppealAuditRequest body = request == null ? new AppealAuditRequest() : request;
        adminService.approveAppeal(id, currentAuditor(), body.getRemark());
        return Result.success();
    }

    @PostMapping("/appeals/{id}/reject")
    public Result<Void> rejectAppeal(
            @PathVariable("id") long id,
            @Valid @RequestBody(required = false) AppealAuditRequest request) {
        AppealAuditRequest body = request == null ? new AppealAuditRequest() : request;
        adminService.rejectAppeal(id, currentAuditor(), body.getRemark());
        return Result.success();
    }

    private String currentAuditor() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new cc.nanoic.yunanexus.common.web.exception.BizException(
                    cc.nanoic.yunanexus.common.web.constant.ErrorCode.FORBIDDEN, "缺少管理员身份");
        if (auth.getPrincipal() instanceof cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity actor) {
            var user = adminService.getUser(actor.userId());
            return user.getUsername();
        }
        if ("admin".equals(auth.getPrincipal()) && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) return "ops:emergency";
        throw new cc.nanoic.yunanexus.common.web.exception.BizException(
                cc.nanoic.yunanexus.common.web.constant.ErrorCode.FORBIDDEN, "无管理权限");
    }

    // ---------- 用户管理 ----------

    @GetMapping("/users")
    public Result<PageResult<UserView>> listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "roleId", required = false) Long roleId,
            @RequestParam(value = "permissionId", required = false) Long permissionId) {
        return Result.success(
                adminService.listUsers(keyword, status, page, size, roleId, permissionId));
    }

    @GetMapping("/users/{userId}")
    public Result<UserView> getUser(@PathVariable("userId") long userId) {
        return Result.success(adminService.getUser(userId));
    }

    @PostMapping("/users/{userId}/enable")
    public Result<Void> enableUser(@PathVariable("userId") long userId) {
        adminService.enableUser(userId);
        return Result.success();
    }

    @PostMapping("/users/{userId}/disable")
    public Result<Void> disableUser(@PathVariable("userId") long userId) {
        adminService.disableUser(userId);
        return Result.success();
    }

    @PostMapping("/users/{userId}/unlock")
    public Result<Void> unlockUser(@PathVariable("userId") long userId) {
        adminService.unlockUser(userId);
        return Result.success();
    }

    @PostMapping("/users/{userId}/kick")
    public Result<Void> kickUser(@PathVariable("userId") long userId) {
        adminService.kickUser(userId);
        return Result.success();
    }
}
