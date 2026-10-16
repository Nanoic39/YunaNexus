package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.domain.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.TokenExchangeRequest;
import cc.nanoic.yunanexus.auth.service.SsoService;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/sso")
public class SsoController {

    private final SsoService ssoService;

    public SsoController(SsoService ssoService) {
        this.ssoService = ssoService;
    }

    @PostMapping("/authorize")
    public Result<Map<String, String>> authorize(
            @Valid @RequestBody AuthorizeRequest request,
            HttpServletRequest httpRequest,
            @org.springframework.security.core.annotation.AuthenticationPrincipal
                    cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity identity) {
        if (identity != null)
            return Result.success(
                    Map.of("redirectUrl", ssoService.authorizeForIdentity(request, identity)));
        String cookieValue = ssoService.readCookie(httpRequest);
        if (cookieValue == null || cookieValue.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO Cookie 缺失");
        }
        String redirectUrl = ssoService.authorize(request, cookieValue);
        return Result.success(Map.of("redirectUrl", redirectUrl));
    }

    @PostMapping("/token")
    public Result<LoginResult> exchange(
            @Valid @RequestBody TokenExchangeRequest request, HttpServletRequest httpRequest) {
        String ssoCookie = ssoService.readCookie(httpRequest);
        if (ssoCookie != null && !ssoCookie.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token 端点不允许携带 SSO Cookie");
        }
        String authorization = httpRequest.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "token 端点不允许携带 Authorization 头");
        }

        LoginResult result = ssoService.exchange(request);
        return Result.success(result);
    }
}
