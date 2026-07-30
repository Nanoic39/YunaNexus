package cc.nanoic.yunanexus.auth.oauth2.controller;

import cc.nanoic.yunanexus.auth.oauth2.exception.InvalidRequestException;
import cc.nanoic.yunanexus.auth.oauth2.exception.OAuth2Exception;
import cc.nanoic.yunanexus.auth.oauth2.model.AuthorizationAction;
import cc.nanoic.yunanexus.auth.oauth2.model.AuthorizationDetailsVO;
import cc.nanoic.yunanexus.auth.oauth2.model.AuthorizationSession;
import cc.nanoic.yunanexus.auth.oauth2.service.OAuth2AuthorizationService;
import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * OAuth2 授权端点控制器.
 *
 * <h3>端点说明</h3>
 * <ul>
 *   <li>GET  /oauth2/authorize             — 第三方发起授权（浏览器重定向入口）</li>
 *   <li>GET  /oauth2/authorize/{sessionId} — 前端获取授权会话详情</li>
 *   <li>POST /oauth2/authorize/{sessionId} — 用户确认/拒绝授权</li>
 * </ul>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2AuthorizationController {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthorizationController.class);

    @Resource
    private OAuth2AuthorizationService authorizationService;

    @Value("${yunanexus.web.base-url}")
    private String webBaseUrl;

    /**
     * 授权端点 — 第三方应用将用户浏览器重定向到此.
     * <p>后端校验所有参数，创建授权会话，然后重定向到前端授权页面.</p>
     */
    @GetMapping("/authorize")
    public void authorize(
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpServletResponse response) throws IOException {

        try {
            // 校验全部参数并创建会话
            AuthorizationSession session = authorizationService.createSession(
                    clientId, redirectUri, responseType, scope, state,
                    codeChallenge, codeChallengeMethod);

            // 如果用户未登录，重定向到登录页
            if (!PermissionContext.hasIdentity()) {
                String loginRedirect = webBaseUrl + "/login?redirect=" +
                        URLEncoder.encode("/oauth/authorize?session=" + session.getSessionId(),
                                StandardCharsets.UTF_8);
                response.sendRedirect(loginRedirect);
                return;
            }

            // 已登录 → 重定向到前端授权页面（只传 sessionId）
            String frontendUrl = webBaseUrl + "/oauth/authorize?session=" + session.getSessionId();
            response.sendRedirect(frontendUrl);

        } catch (OAuth2Exception e) {
            // 校验失败 → 回跳 redirect_uri 带错误信息
            // 如果校验在 redirect_uri 确定之前失败，无法回跳，直接返回错误
            if (e.isRedirectError()) {
                response.sendRedirect(e.buildErrorRedirectUri());
            } else {
                response.sendError(e.getHttpStatus(), e.getErrorDescription());
            }
        }
    }

    /**
     * 获取授权会话详情 — 前端授权页面调用.
     * <p>需要用户已登录.</p>
     */
    @GetMapping("/authorize/{sessionId}")
    public Result<AuthorizationDetailsVO> getSessionDetails(
            @PathVariable("sessionId") String sessionId) {

        if (!PermissionContext.hasIdentity()) {
            return Result.fail(401, "请先登录后再授权");
        }

        AuthorizationDetailsVO details = authorizationService.getSessionDetails(sessionId);
        if (details == null) {
            return Result.fail(400, "授权会话已过期或不存在");
        }
        return Result.success(details);
    }

    /**
     * 用户确认或拒绝授权.
     * <p>需要用户已登录，携带 Bearer Token.</p>
     */
    @PostMapping("/authorize/{sessionId}")
    public Result<Map<String, String>> handleAuthorization(
            @PathVariable("sessionId") String sessionId,
            @RequestBody AuthorizationAction action) {

        if (!PermissionContext.hasIdentity()) {
            return Result.fail(401, "请先登录后再授权");
        }

        if (action.getAction() == null) {
            throw new InvalidRequestException("missing action (approve or deny)");
        }

        Map<String, String> result;
        if (action.isApproved()) {
            result = authorizationService.approve(sessionId);
        } else if (action.isDenied()) {
            result = authorizationService.deny(sessionId);
        } else {
            throw new InvalidRequestException("action must be 'approve' or 'deny'");
        }

        return Result.success(result);
    }
}
