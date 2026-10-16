package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.PasswordRecoveryService;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class PasswordRecoveryController {
    private final PasswordRecoveryService passwords;

    @org.springframework.beans.factory.annotation.Autowired
    private cc.nanoic.yunanexus.auth.client.RootAdminClient roles;

    public PasswordRecoveryController(PasswordRecoveryService passwords) {
        this.passwords = passwords;
    }

    public record Request(String username, String email) {}

    public record Verify(String username, String email, String code) {}

    public record Complete(String ticket, String password) {}

    public record Change(String oldPassword, String password) {}

    @PostMapping("/api/v1/auth/password-reset/request")
    public Result<?> request(@RequestBody Request b, HttpServletRequest r) {
        String ip = r.getHeader("X-Real-IP");
        passwords.request(b.username(), b.email(), ip == null ? r.getRemoteAddr() : ip);
        return Result.success();
    }

    @PostMapping("/api/v1/auth/password-reset/verify")
    public Result<?> verify(@RequestBody Verify b) {
        return Result.success(
                java.util.Map.of("ticket", passwords.verify(b.username(), b.email(), b.code())));
    }

    @PostMapping("/api/v1/auth/password-reset/complete")
    public Result<?> complete(@RequestBody Complete b) {
        passwords.complete(b.ticket(), b.password());
        return Result.success();
    }

    public record EmailRequest(String email,String oldPassword){}
    public record EmailVerify(String email,String code){}
    private long owner(AuthenticatedIdentity i){if(i==null||!"web".equals(i.clientCode()))throw new BizException(ErrorCode.FORBIDDEN);return i.userId();}
    @PostMapping("/api/v1/auth/account/email/request") public Result<?> requestEmail(@AuthenticationPrincipal AuthenticatedIdentity i,@RequestBody EmailRequest b){passwords.requestEmail(owner(i),b.email(),b.oldPassword());return Result.success();}
    @PostMapping("/api/v1/auth/account/email/verify") public Result<?> verifyEmail(@AuthenticationPrincipal AuthenticatedIdentity i,@RequestBody EmailVerify b){passwords.bindEmail(owner(i),b.email(),b.code());return Result.success();}
    @GetMapping("/api/v1/auth/account/security")
    public Result<?> security(@AuthenticationPrincipal AuthenticatedIdentity i) {
        if (i == null || !"web".equals(i.clientCode())) throw new BizException(ErrorCode.FORBIDDEN);
        return Result.success(passwords.security(i.userId()));
    }

    @PostMapping("/api/v1/auth/account/password")
    public Result<?> change(
            @AuthenticationPrincipal AuthenticatedIdentity i, @RequestBody Change b) {
        if (i == null || !"web".equals(i.clientCode())) throw new BizException(ErrorCode.FORBIDDEN);
        passwords.change(i.userId(), b.oldPassword(), b.password());
        return Result.success();
    }

    @GetMapping("/api/v1/auth/admin/password-reset-records")
    public Result<?> records(@AuthenticationPrincipal AuthenticatedIdentity i) {
        if (i == null || !"web".equals(i.clientCode())) throw new BizException(ErrorCode.FORBIDDEN);
        var r = roles.hasCode(i.userId(), "account:reset-records:read");
        if (r == null || r.getCode() != 200 || !Boolean.TRUE.equals(r.getData()))
            throw new BizException(ErrorCode.FORBIDDEN, "无密码重置审计权限");
        return Result.success(passwords.records());
    }
}
