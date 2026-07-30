package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.entity.DTO.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenRequest;
import cc.nanoic.yunanexus.auth.entity.DTO.TokenResponse;
import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.entity.VO.OAuthClientVO;
import cc.nanoic.yunanexus.auth.service.OAuthService;
import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    @Resource
    private OAuthService oAuthService;

    /**
     * 前端站点 URL，用于未登录时跳转登录页。
     * 默认 http://localhost:3000，生产环境通过 yunanexus.web.base-url 配置。
     */
    @Value("${yunanexus.web.base-url}")
    private String webBaseUrl;

    /**
     * OAuth 2.0 授权端点 — 浏览器跳转（GET）
     *
     * @deprecated 请使用 {@code /oauth2/authorize}（新端点带完整参数校验）.
     *             此端点保留向后兼容，但增加了基本的 client_id / redirect_uri 校验.
     */
    @Deprecated
    @GetMapping("/authorize")
    public void authorizeGet(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // 基本安全校验：确认客户端存在且 redirect_uri 匹配
        try {
            OAuthClient client = oAuthService.findActiveClientForAuth(clientId);
            if (!redirectUri.equals(client.getRedirectUri())) {
                response.sendError(400, "redirect_uri mismatch");
                return;
            }
        } catch (Exception e) {
            response.sendError(400, "invalid client_id or redirect_uri");
            return;
        }

        String queryString = request.getQueryString();
        String frontendUrl = webBaseUrl + "/oauth/authorize";
        if (queryString != null) {
            frontendUrl += "?" + queryString;
        }
        response.sendRedirect(frontendUrl);
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