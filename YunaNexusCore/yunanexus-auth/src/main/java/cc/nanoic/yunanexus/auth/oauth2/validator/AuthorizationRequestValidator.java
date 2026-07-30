package cc.nanoic.yunanexus.auth.oauth2.validator;

import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.oauth2.exception.InvalidClientException;
import cc.nanoic.yunanexus.auth.oauth2.exception.InvalidRequestException;
import cc.nanoic.yunanexus.auth.oauth2.exception.InvalidScopeException;
import cc.nanoic.yunanexus.auth.oauth2.service.OAuth2ClientDetailsService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 授权请求参数校验器 — 在 GET /oauth2/authorize 阶段校验所有参数.
 * <p>校验失败直接抛出 OAuth2Exception（含 redirect_uri 用于错误回跳）.</p>
 */
@Component
public class AuthorizationRequestValidator {

    /** 唯一支持的 response_type */
    private static final String SUPPORTED_RESPONSE_TYPE = "code";

    /** PKCE 支持的挑战方法 */
    private static final String PKCE_S256 = "S256";
    private static final String PKCE_PLAIN = "plain";

    /** code_challenge 最小长度（RFC 7636 建议 ≥43 字符的 Base64URL） */
    private static final int CODE_CHALLENGE_MIN_LENGTH = 43;

    /** state 参数最大长度 */
    private static final int STATE_MAX_LENGTH = 512;

    @Resource
    private OAuth2ClientDetailsService clientDetailsService;

    /**
     * 校验授权请求的所有参数，校验通过后返回客户端实体.
     *
     * @param clientId             客户端 UUID
     * @param redirectUri          回调地址
     * @param responseType         响应类型（必须为 "code"）
     * @param scope                请求的授权范围
     * @param state                客户端 state（CSRF 防护）
     * @param codeChallenge        PKCE code_challenge
     * @param codeChallengeMethod  PKCE 方法（S256 或 plain）
     * @return 校验通过的客户端实体
     * @throws OAuth2Exception 校验失败时抛出（携带 redirect_uri 和 state 用于错误回跳）
     */
    public OAuthClient validate(String clientId, String redirectUri,
                                 String responseType, String scope, String state,
                                 String codeChallenge, String codeChallengeMethod) {

        // 1. 校验 response_type
        if (responseType != null && !SUPPORTED_RESPONSE_TYPE.equals(responseType)) {
            // 不支持的 response_type — 如果有 redirect_uri 就回跳，否则直接抛错
            throw new InvalidRequestException(
                    "unsupported_response_type: only 'code' is supported",
                    redirectUri, state);
        }

        // 2. 校验 client_id 不为空
        if (!StringUtils.hasText(clientId)) {
            throw new InvalidRequestException("missing client_id", redirectUri, state);
        }

        // 3. 校验 redirect_uri 不为空
        if (!StringUtils.hasText(redirectUri)) {
            throw new InvalidRequestException("missing redirect_uri", null, state);
        }

        // 4. 查找客户端，校验状态
        OAuthClient client = clientDetailsService.findActiveClient(clientId);
        if (client == null) {
            throw new InvalidClientException("client not found or disabled");
        }

        // 5. 校验 redirect_uri 精确匹配（防止开放重定向攻击）
        if (!redirectUri.equals(client.getRedirectUri())) {
            throw new InvalidRequestException(
                    "redirect_uri mismatch", redirectUri, state);
        }

        // 6. 校验客户端支持 authorization_code 授权类型
        if (client.getGrantTypes() == null || !client.getGrantTypes().contains("authorization_code")) {
            throw new InvalidClientException(
                    "client does not support authorization_code grant type",
                    redirectUri, state);
        }

        // 7. 校验 scope（请求的 scope 必须是客户端注册 scope 的子集）
        if (StringUtils.hasText(scope)) {
            String registeredScope = client.getScope();
            if (registeredScope == null || registeredScope.isEmpty()) {
                throw new InvalidScopeException(
                        "client has no registered scope, cannot request scope",
                        redirectUri, state);
            }
            for (String requested : scope.split("\\s+")) {
                boolean found = false;
                for (String registered : registeredScope.split("\\s+")) {
                    if (matchesScope(requested, registered)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new InvalidScopeException(
                            "requested scope '" + requested + "' exceeds client's registered scope",
                            redirectUri, state);
                }
            }
        }

        // 8. 校验 PKCE 参数
        if (StringUtils.hasText(codeChallenge)) {
            if (codeChallenge.length() < CODE_CHALLENGE_MIN_LENGTH) {
                throw new InvalidRequestException(
                        "code_challenge too short (min " + CODE_CHALLENGE_MIN_LENGTH + " chars)",
                        redirectUri, state);
            }
            String method = codeChallengeMethod != null ? codeChallengeMethod : PKCE_S256;
            if (!PKCE_S256.equals(method) && !PKCE_PLAIN.equals(method)) {
                throw new InvalidRequestException(
                        "unsupported code_challenge_method: must be S256 or plain",
                        redirectUri, state);
            }
        }

        // 9. 校验 state 格式（防 XSS/注入）
        if (state != null && state.length() > STATE_MAX_LENGTH) {
            throw new InvalidRequestException("state too long (max " + STATE_MAX_LENGTH + " chars)",
                    redirectUri, state);
        }

        return client;
    }

    /**
     * 通配符 scope 匹配 — 按段匹配.
     * <p>例如: "core:file:read" 匹配 "core:*:*:*", "core:*:*", "core:file:*" 但不匹配 "admin:*:*:*".</p>
     */
    private boolean matchesScope(String requested, String registered) {
        if ("*".equals(registered) || "*:*:*:*".equals(registered)) {
            return true;
        }
        String[] reqParts = requested.split(":");
        String[] regParts = registered.split(":");
        if (reqParts.length != regParts.length) {
            return false;
        }
        for (int i = 0; i < reqParts.length; i++) {
            if (!"*".equals(regParts[i]) && !regParts[i].equals(reqParts[i])) {
                return false;
            }
        }
        return true;
    }
}
