package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.domain.CaptchaResult;
import cc.nanoic.yunanexus.auth.domain.EmailCodeRequest;
import cc.nanoic.yunanexus.auth.domain.LoginRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.MeResponse;
import cc.nanoic.yunanexus.auth.domain.RefreshRequest;
import cc.nanoic.yunanexus.auth.domain.RegisterRequest;
import cc.nanoic.yunanexus.auth.domain.UnlockAppealRequest;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.AuthService;
import cc.nanoic.yunanexus.auth.service.CaptchaService;
import cc.nanoic.yunanexus.auth.service.RegisterService;
import cc.nanoic.yunanexus.auth.service.SsoService;
import cc.nanoic.yunanexus.auth.service.UnlockAppealService;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Resource private AuthService authService;

    @Resource private RegisterService registerService;

    @Resource private UnlockAppealService unlockAppealService;

    @Resource private CaptchaService captchaService;

    @Resource private SsoService ssoService;
    @Resource private cc.nanoic.yunanexus.auth.service.impl.EmailCodeService emailCodeService;

    /**
     * 登录（含分级处置）。
     *
     * <p>登录成功与失败都会进入审计链路：失败次数、冻结等细节由 auth 侧落表，
     * 审计记录提供"谁在什么时候从哪个 IP 尝试登录"的独立凭据。
     */
    @cc.nanoic.yunanexus.common.audit.annotation.AuditAction(
            module = "auth", action = "LOGIN", risk = "HIGH")
    @PostMapping("/login")
    public Result<LoginResult> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginResult result = authService.login(request, clientIp(httpRequest));
        ssoService.establish(result, httpRequest, httpResponse);
        return Result.success(result);
    }

    /**
     * 发送按用途隔离的邮箱验证码；找回密码使用独立流程。
     *
     * @param body 请求体，含 email 和可选 purpose（默认 REGISTER）
     * @return 是否已发送
     */
    @PostMapping("/email-code")
    public Result<Boolean> sendEmailCode(
            @Valid @RequestBody EmailCodeRequest body, HttpServletRequest request) {
        return Result.success(emailCodeService.send(body.getEmail(), body.getPurpose(), clientIp(request)));
    }

    /**
     * 邮箱验证码登录（免密码）。
     */
    @cc.nanoic.yunanexus.common.audit.annotation.AuditAction(
            module = "auth", action = "LOGIN_EMAIL_CODE", risk = "HIGH")
    @PostMapping("/login/email-code")
    public Result<LoginResult> loginByEmailCode(
            @Valid @RequestBody cc.nanoic.yunanexus.auth.domain.EmailCodeLoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginResult result = authService.loginByEmailCode(request, clientIp(httpRequest));
        ssoService.establish(result, httpRequest, httpResponse);
        return Result.success(result);
    }

    @PostMapping("/refresh")
    public Result<LoginResult> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.success(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @AuthenticationPrincipal AuthenticatedIdentity identity) {
        if (identity == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring("Bearer ".length()).trim());
        }
        ssoService.clear(httpRequest, httpResponse);
        return Result.success();
    }

    @PostMapping("/logout-all")
    public Result<Void> logoutAll(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @AuthenticationPrincipal AuthenticatedIdentity identity) {
        if (identity == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        authService.logoutAll(identity);
        ssoService.clear(httpRequest, httpResponse);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<MeResponse> me(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        if (identity == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        return Result.success(authService.me(identity));
    }

    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(registerService.register(request));
    }

    @PostMapping("/unlock-appeal")
    public Result<Long> unlockAppeal(@Valid @RequestBody UnlockAppealRequest request) {
        return Result.success(unlockAppealService.submit(request));
    }

    @GetMapping("/captcha")
    public Result<CaptchaResult> captcha(@RequestParam("username") String username) {
        if (username == null || username.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "username 不能为空");
        }
        return Result.success(captchaService.issue(username.trim()));
    }

    @GetMapping("/csrf")
    public Result<Map<String, String>> csrf(CsrfToken csrfToken) {
        return Result.success(
                Map.of(
                        "token", csrfToken.getToken(),
                        "headerName", csrfToken.getHeaderName(),
                        "parameterName", csrfToken.getParameterName()));
    }

    @org.springframework.beans.factory.annotation.Value(
            "${yunanexus.auth.trusted-proxy-cidrs:127.0.0.1/32,::1/128}")
    private String trustedProxyCidrs = "127.0.0.1/32,::1/128";

    private String clientIp(HttpServletRequest request) {
        return cc.nanoic.yunanexus.auth.security.ClientIpResolver.resolve(
                request, trustedProxyCidrs);
    }
}
