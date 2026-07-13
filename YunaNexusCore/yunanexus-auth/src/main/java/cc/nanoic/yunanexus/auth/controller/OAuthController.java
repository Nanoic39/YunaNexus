package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenResponse;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthClientVO;
import cc.nanoic.yunanexus.auth.service.OAuthService;
import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Resource
    private OAuthService oAuthService;

    /**
     * OAuth 2.0 授权端点 — 浏览器跳转（GET）
     * 未登录则重定向到登录页，登录后回到此处；
     * 已登录则生成授权码并重定向到回调地址。
     */
    @GetMapping("/authorize")
    public void authorizeGet(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletResponse response) throws IOException {

        if (!PermissionContext.hasIdentity()) {
            // 未登录 → 重定向到登录页，登录后回到当前地址
            String currentUrl = "/oauth/authorize?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + (responseType != null ? "&response_type=" + URLEncoder.encode(responseType, StandardCharsets.UTF_8) : "")
                    + (scope != null ? "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8) : "")
                    + (state != null ? "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8) : "")
                    + (codeChallenge != null ? "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8) : "")
                    + (codeChallengeMethod != null ? "&code_challenge_method=" + URLEncoder.encode(codeChallengeMethod, StandardCharsets.UTF_8) : "");
            response.sendRedirect("/login?redirect=" + URLEncoder.encode(currentUrl, StandardCharsets.UTF_8));
            return;
        }

        AuthorizeRequest req = new AuthorizeRequest();
        req.setClientId(clientId);
        req.setRedirectUri(redirectUri);
        req.setResponseType(responseType);
        req.setScope(scope);
        req.setState(state);
        req.setCodeChallenge(codeChallenge);
        req.setCodeChallengeMethod(codeChallengeMethod);

        Map<String, String> result = oAuthService.authorize(req);
        // 重定向到 redirect_uri?code=xxx&state=yyy
        String location = result.get("redirectUri");
        location += (location.contains("?") ? "&" : "?") + "code=" + result.get("code");
        if (result.get("state") != null && !result.get("state").isEmpty()) {
            location += "&state=" + URLEncoder.encode(result.get("state"), StandardCharsets.UTF_8);
        }
        response.sendRedirect(location);
    }

    /**
     * OAuth 2.0 授权端点 — API 调用（POST）
     * 需要已登录（Bearer Token），直接返回授权码 JSON
     */
    @PostMapping("/authorize")
    public Result<Map<String, String>> authorize(@RequestBody AuthorizeRequest request) {
        if (!PermissionContext.hasIdentity()) {
            return Result.fail(401, "请先登录后再授权");
        }
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