package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.RegisterClientRequest;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthClientVO;
import cc.nanoic.yunanexus.auth.service.OAuthService;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/oauth/client")
public class OAuthClientController {

    @Resource
    private OAuthService oAuthService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterClientRequest req) {
        return Result.success(oAuthService.registerClient(req));
    }

    @GetMapping("/{uuid}")
    public Result<OAuthClientVO> detail(@PathVariable String uuid) {
        return Result.success(oAuthService.getClientDetail(uuid));
    }

    @PutMapping("/{uuid}")
    public Result<?> update(@PathVariable String uuid, @RequestBody RegisterClientRequest req) {
        oAuthService.updateClient(uuid, req);
        return Result.success(null);
    }

    @PostMapping("/{uuid}/audit")
    public Result<?> audit(@PathVariable String uuid,
                           @RequestParam Integer auditStatus,
                           @RequestParam(required = false) String auditOpinion) {
        oAuthService.auditClient(uuid, auditStatus, auditOpinion);
        return Result.success(null);
    }

    @PostMapping("/{uuid}/toggle")
    public Result<?> toggle(@PathVariable String uuid) {
        oAuthService.toggleClient(uuid);
        return Result.success(null);
    }
}