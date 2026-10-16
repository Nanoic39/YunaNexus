package cc.nanoic.yunanexus.auth.controller;
import cc.nanoic.yunanexus.auth.client.RootAdminClient;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 会话已由 Spring Security 全量复验，仅向内网返回管理权限布尔值。 */
@RestController
public class InternalAdminController {
    private final RootAdminClient roles;
    public InternalAdminController(RootAdminClient roles) { this.roles=roles; }
    @GetMapping("/internal/auth/admin/check")
    public Result<Boolean> check(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        if (identity == null) throw new IllegalStateException("Authenticated identity required");
        Result<Boolean> result=roles.check(identity.userId());
        if (result==null || result.getCode()!=200) throw new IllegalStateException("Role verification unavailable");
        return Result.success(Boolean.TRUE.equals(result.getData()));
    }
    @GetMapping("/internal/auth/admin/super-check") public Result<Boolean> superCheck(@AuthenticationPrincipal AuthenticatedIdentity i){if(i==null||!"web".equals(i.clientCode()))return Result.success(false);return roles.superCheck(i.userId());}
    public record Actor(long userId,boolean superAdmin){}
    @GetMapping("/internal/auth/admin/identity") public Result<Actor> actor(@AuthenticationPrincipal AuthenticatedIdentity i){if(i==null||!"web".equals(i.clientCode()))throw new IllegalStateException("Portal session required");var r=roles.superCheck(i.userId());if(r==null||r.getCode()!=200)throw new IllegalStateException("Role unavailable");return Result.success(new Actor(i.userId(),Boolean.TRUE.equals(r.getData())));}
}
