package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.RegisterClientRequest;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthClientVO;
import cc.nanoic.yunanexus.auth.service.OAuthService;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth/client")
public class OAuthClientController {

    @Resource
    private OAuthService oAuthService;

    @GetMapping("/list")
    @RequirePermission
    public Result<List<OAuthClientVO>> list() {
        return Result.success(oAuthService.listClients());
    }

    @PostMapping("/register")
    @RequirePermission
    public Result<Map<String, Object>> register(@RequestBody RegisterClientRequest req) {
        return Result.success(oAuthService.registerClient(req));
    }

    @GetMapping("/{uuid}")
    @RequirePermission
    public Result<OAuthClientVO> detail(@PathVariable("uuid") String uuid) {
        return Result.success(oAuthService.getClientDetail(uuid));
    }

    @GetMapping("/{uuid}/secret")
    @RequirePermission
    public Result<String> secret(@PathVariable("uuid") String uuid) {
        return Result.success(oAuthService.getClientSecret(uuid));
    }

    @PutMapping("/{uuid}")
    @RequirePermission
    public Result<?> update(@PathVariable("uuid") String uuid, @RequestBody RegisterClientRequest req) {
        oAuthService.updateClient(uuid, req);
        return Result.success(null);
    }

    @PostMapping("/{uuid}/audit")
    @RequirePermission(anyOf = { "admin:oauth:audit:approve", "admin:oauth:audit:reject", "core:oauth:audit", "admin:*" })
    public Result<?> audit(@PathVariable("uuid") String uuid,
            @RequestParam("auditStatus") Integer auditStatus,
            @RequestParam(value = "auditOpinion", required = false) String auditOpinion) {
        oAuthService.auditClient(uuid, auditStatus, auditOpinion);
        return Result.success(null);
    }

    @PostMapping("/{uuid}/toggle")
    @RequirePermission(anyOf = { "admin:oauth:toggle", "core:oauth:audit", "admin:*" })
    public Result<?> toggle(@PathVariable("uuid") String uuid) {
        oAuthService.toggleClient(uuid);
        return Result.success(null);
    }

    @DeleteMapping("/{uuid}")
    @RequirePermission(anyOf = { "admin:oauth:client:delete", "core:oauth:audit", "admin:*" })
    public Result<?> delete(@PathVariable("uuid") String uuid) {
        oAuthService.deleteClient(uuid);
        return Result.success(null);
    }
}
