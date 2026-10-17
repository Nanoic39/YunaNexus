package cc.nanoic.yunanexus.auth.controller;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.ApplicationAccessService;
import cc.nanoic.yunanexus.common.core.model.Result;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class ApplicationAccessController {
 private final ApplicationAccessService access;public ApplicationAccessController(ApplicationAccessService access){this.access=access;}
 @GetMapping("/api/v1/auth/account/oauth-apps/{id}/access") public Result<?> catalog(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("id") long id){return Result.success(access.catalog(actor,id));}
 @PostMapping("/api/v1/auth/account/oauth-apps/{id}/access/permissions") public Result<?> permission(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("id") long id,@RequestBody ApplicationAccessService.Permission body){access.permission(actor,id,body);return Result.success();}
 @PostMapping("/api/v1/auth/account/oauth-apps/{id}/access/roles") public Result<?> role(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("id") long id,@RequestBody ApplicationAccessService.Role body){access.role(actor,id,body);return Result.success();}
 @DeleteMapping("/api/v1/auth/account/oauth-apps/{id}/access/permissions/{code}") public Result<?> deletePermission(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("id") long id,@PathVariable("code") String code){access.delete(actor,id,code,false);return Result.success();}
 @DeleteMapping("/api/v1/auth/account/oauth-apps/{id}/access/roles/{code}") public Result<?> deleteRole(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("id") long id,@PathVariable("code") String code){access.delete(actor,id,code,true);return Result.success();}
 @GetMapping("/api/v1/auth/account/oauth-apps/{id}/access/members") public Result<?> memberRoles(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("id") long id,@RequestParam("externalId") String externalId){return Result.success(access.memberRoles(actor,id,externalId));}
 @PostMapping("/api/v1/auth/account/oauth-apps/{id}/access/members") public Result<?> member(@AuthenticationPrincipal AuthenticatedIdentity actor,@PathVariable("id") long id,@RequestBody ApplicationAccessService.Member body){access.member(actor,id,body);return Result.success();}
 @PostMapping("/api/v1/auth/application/permissions/check") public Result<?> check(@AuthenticationPrincipal AuthenticatedIdentity actor,@RequestBody ApplicationAccessService.Check body){return Result.success(access.check(actor,body));}
}
