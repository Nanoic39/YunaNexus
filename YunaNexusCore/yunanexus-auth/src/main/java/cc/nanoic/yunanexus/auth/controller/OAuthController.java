package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenResponse;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthClientVO;
import cc.nanoic.yunanexus.auth.service.OAuthService;
import cc.nanoic.yunanexus.common.web.auth.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Resource
    private OAuthService oAuthService;

    @PostMapping("/authorize")
    @RequirePermission
    public Result<Map<String, String>> authorize(@RequestBody AuthorizeRequest request) {
        return Result.success(oAuthService.authorize(request));
    }

    @PostMapping("/token")
    public Result<TokenResponse> token(@RequestBody TokenRequest request) {
        return Result.success(oAuthService.token(request));
    }

    @GetMapping("/clients")
    public Result<List<OAuthClientVO>> listClients() {
        return Result.success(oAuthService.listClients());
    }
}