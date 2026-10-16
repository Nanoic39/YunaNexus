package cc.nanoic.yunanexus.auth.controller;

import cc.nanoic.yunanexus.auth.domain.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.*;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/account/oauth-consent")
public class OAuthConsentController {
    private final OAuthApplicationService apps;
    private final SsoService sso;

    public OAuthConsentController(OAuthApplicationService apps, SsoService sso) {
        this.apps = apps;
        this.sso = sso;
    }

    private long user(AuthenticatedIdentity i) {
        if (i == null || !"web".equals(i.clientCode())) throw new BizException(ErrorCode.FORBIDDEN);
        return i.userId();
    }

    @GetMapping
    public Result<Map<String, Object>> preview(
            @AuthenticationPrincipal AuthenticatedIdentity i,
            @RequestParam("clientCode") String code,
            @RequestParam("redirectUri") String uri) {
        user(i);
        return Result.success(apps.consentPreview(code, uri));
    }

    @PostMapping
    public Result<Map<String, String>> consent(
            @AuthenticationPrincipal AuthenticatedIdentity i,
            @Valid @RequestBody AuthorizeRequest request) {
        long uid = user(i);
        apps.consent(uid, request.getClientCode(), request.getRedirectUri());
        return Result.success(Map.of("redirectUrl", sso.authorizeForIdentity(request, i)));
    }
}
