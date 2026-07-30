package cc.nanoic.yunanexus.auth.oauth2.controller;

import cc.nanoic.yunanexus.auth.oauth2.exception.OAuth2Exception;
import cc.nanoic.yunanexus.auth.oauth2.model.RevocationRequest;
import cc.nanoic.yunanexus.auth.oauth2.model.TokenRequest;
import cc.nanoic.yunanexus.auth.oauth2.model.TokenResponse;
import cc.nanoic.yunanexus.auth.oauth2.service.OAuth2TokenService;
import cc.nanoic.yunanexus.common.web.common.Result;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * OAuth2 Token 端点与吊销端点控制器.
 *
 * <h3>端点说明</h3>
 * <ul>
 *   <li>POST /oauth2/token  — 用授权码换 Token / 刷新 Token</li>
 *   <li>POST /oauth2/revoke — 吊销 Token (RFC 7009)</li>
 * </ul>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2TokenController {

    private static final Logger log = LoggerFactory.getLogger(OAuth2TokenController.class);

    @Resource
    private OAuth2TokenService tokenService;

    /**
     * Token 端点 (RFC 6749 §4.1.3).
     * <p>客户端通过 client_id + client_secret 认证，无需用户 Bearer Token.</p>
     *
     * @param request 请求体
     * @return TokenResponse (access_token, refresh_token, etc.)
     */
    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody TokenRequest request) {
        try {
            TokenResponse resp = tokenService.token(request);
            return ResponseEntity.ok(resp);
        } catch (OAuth2Exception e) {
            log.warn("OAuth2 token endpoint error: error={}, description={}",
                    e.getErrorCode(), e.getErrorDescription());
            return ResponseEntity.status(e.getHttpStatus())
                    .body(e.toTokenErrorResponse());
        }
    }

    /**
     * Token 吊销端点 (RFC 7009).
     * <p>吊销成功后返回 HTTP 200，即使 token 不存在也返回 200（防止信息泄露）.</p>
     */
    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(@RequestBody RevocationRequest request) {
        tokenService.revoke(request.getToken(), request.getTokenTypeHint());
        // RFC 7009: 无论成功与否都返回 200
        return ResponseEntity.ok().build();
    }
}
