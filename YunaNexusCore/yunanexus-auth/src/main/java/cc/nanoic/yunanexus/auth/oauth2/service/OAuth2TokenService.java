package cc.nanoic.yunanexus.auth.oauth2.service;

import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.oauth2.config.OAuth2Properties;
import cc.nanoic.yunanexus.auth.oauth2.exception.*;
import cc.nanoic.yunanexus.auth.oauth2.model.OAuth2GrantType;
import cc.nanoic.yunanexus.auth.oauth2.model.TokenRequest;
import cc.nanoic.yunanexus.auth.oauth2.model.TokenResponse;
import cc.nanoic.yunanexus.auth.service.AuthService;
import cc.nanoic.yunanexus.auth.entity.DTO.LoginResponse;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.digest.DigestUtil;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * OAuth2 Token 服务 — 负责授权码换 Token、刷新 Token、吊销 Token.
 * <p>严格遵循 RFC 6749 §4.1.3 (授权码换 Token) 和 §6 (刷新 Token).</p>
 */
@Service
public class OAuth2TokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2TokenService.class);

    private static final String REFRESH_KEY_PREFIX = "oauth2:refresh:";
    private static final String REVOKED_JTI_PREFIX = "oauth2:revoked:jti:";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private OAuth2Properties oAuth2Properties;

    @Resource
    private OAuth2ClientDetailsService clientDetailsService;

    @Resource
    private OAuth2AuthorizationService authorizationService;

    @Resource
    private AuthService authService;

    /**
     * Token 端点入口 — 根据 grant_type 分发到对应的处理器.
     */
    public TokenResponse token(TokenRequest request) {
        // 1. 校验 grant_type
        if (request.getGrantType() == null || request.getGrantType().isEmpty()) {
            throw new InvalidRequestException("missing grant_type");
        }

        OAuth2GrantType grantType = OAuth2GrantType.from(request.getGrantType());
        if (grantType == null) {
            throw new UnsupportedGrantTypeException(
                    "unsupported grant_type: " + request.getGrantType());
        }

        return switch (grantType) {
            case AUTHORIZATION_CODE -> handleAuthorizationCode(request);
            case REFRESH_TOKEN -> handleRefreshToken(request);
        };
    }

    /**
     * 处理 authorization_code 授权类型.
     */
    private TokenResponse handleAuthorizationCode(TokenRequest request) {
        // 1. 认证客户端
        OAuthClient client = authenticate(request.getClientId(), request.getClientSecret());

        // 2. 校验授权码（原子删除，防止重放）
        Map<String, Object> codeData = authorizationService.consumeCode(request.getCode());
        if (codeData == null) {
            throw new InvalidGrantException("authorization code is invalid or has expired");
        }

        // 3. 校验 redirect_uri 与授权时一致
        String storedRedirectUri = (String) codeData.get("redirectUri");
        if (!storedRedirectUri.equals(request.getRedirectUri())) {
            throw new InvalidGrantException("redirect_uri mismatch");
        }

        // 4. 校验 client_id 与授权时一致
        String storedClientId = (String) codeData.get("clientId");
        if (!storedClientId.equals(request.getClientId())) {
            throw new InvalidGrantException("client_id mismatch with authorization code");
        }

        // 5. PKCE 验证
        String storedChallenge = (String) codeData.get("codeChallenge");
        if (storedChallenge != null) {
            if (request.getCodeVerifier() == null || request.getCodeVerifier().isEmpty()) {
                throw new InvalidGrantException("PKCE: missing code_verifier");
            }
            String method = (String) codeData.get("codeChallengeMethod");
            String computed = "plain".equalsIgnoreCase(method)
                    ? request.getCodeVerifier()
                    : Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(DigestUtil.sha256(request.getCodeVerifier()));
            if (!computed.equals(storedChallenge)) {
                throw new InvalidGrantException("PKCE: code_verifier verification failed");
            }
        }

        // 6. 从授权码数据中提取用户和 scope 信息
        byte[] globalId = HexUtil.decodeHex((String) codeData.get("globalId"));
        String scope = (String) codeData.get("scope");

        // 7. 签发令牌（先把 Token 构建完成，再操作 Redis — 防止构建失败导致数据丢失）
        LoginResponse loginResp = authService.issueTokens(globalId);

        // 8. 存储 refresh_token 到 Redis（OAuth2 专用前缀）
        String refreshToken = loginResp.getRefreshToken();
        if (refreshToken != null && !refreshToken.isEmpty()) {
            storeRefreshToken(refreshToken, (String) codeData.get("clientId"),
                    HexUtil.encodeHexStr(globalId), scope);
        }

        log.info("OAuth2 token issued: grant_type=authorization_code, clientId={}, userId={}, scope={}",
                request.getClientId(), codeData.get("globalId"), scope);

        // 9. 构建 RFC 6749 标准响应
        TokenResponse resp = new TokenResponse();
        resp.setAccessToken(loginResp.getAccessToken());
        resp.setTokenType("Bearer");
        resp.setExpiresIn(loginResp.getExpiresIn());
        resp.setRefreshToken(refreshToken);
        resp.setScope(scope);
        return resp;
    }

    /**
     * 处理 refresh_token 授权类型.
     */
    private TokenResponse handleRefreshToken(TokenRequest request) {
        // 1. 认证客户端
        OAuthClient client = authenticate(request.getClientId(), request.getClientSecret());

        // 2. 加载并删除旧的 refresh_token（轮转策略）
        String refreshKey = REFRESH_KEY_PREFIX + request.getRefreshToken();
        RBucket<String> bucket = redissonClient.getBucket(refreshKey);
        String data = bucket.get();
        if (data == null) {
            throw new InvalidGrantException("refresh token is invalid or has expired");
        }
        bucket.delete();

        // 3. 解析 refresh token 数据
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenData = com.alibaba.fastjson2.JSON.parseObject(data, Map.class);
        String storedClientId = (String) tokenData.get("clientId");
        String globalIdHex = (String) tokenData.get("globalId");
        String scope = (String) tokenData.get("scope");

        // 4. 验证 refresh token 属于该客户端
        if (!storedClientId.equals(request.getClientId())) {
            throw new InvalidGrantException("refresh token does not belong to this client");
        }

        // 5. 签发新令牌
        byte[] globalId = HexUtil.decodeHex(globalIdHex);
        LoginResponse loginResp = authService.issueTokens(globalId);

        // 6. 存储新的 refresh_token（轮转）
        String newRefreshToken = loginResp.getRefreshToken();
        if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
            storeRefreshToken(newRefreshToken, request.getClientId(), globalIdHex, scope);
        }

        log.info("OAuth2 token refreshed: clientId={}, userId={}, scope={}",
                request.getClientId(), globalIdHex, scope);

        // 7. 构建响应
        TokenResponse resp = new TokenResponse();
        resp.setAccessToken(loginResp.getAccessToken());
        resp.setTokenType("Bearer");
        resp.setExpiresIn(loginResp.getExpiresIn());
        resp.setRefreshToken(newRefreshToken);
        resp.setScope(scope);
        return resp;
    }

    /**
     * 吊销 Token (RFC 7009).
     */
    public void revoke(String token, String tokenTypeHint) {
        if (token == null || token.isEmpty()) {
            // RFC 7009: 即使 token 为空也返回 200（不泄露信息）
            return;
        }

        // 尝试作为 refresh_token 删除
        String refreshKey = REFRESH_KEY_PREFIX + token;
        boolean deleted = redissonClient.getBucket(refreshKey).delete();

        if (!deleted && "access_token".equals(tokenTypeHint)) {
            // 对于 access_token：将 jti 加入黑名单，TTL 为剩余有效时间
            // 注：当前项目使用 Hutool JWT，不含 jti 声明。
            // 简单处理：如果是 access_token 直接忽略（JWT 本身无法主动吊销，
            // 需配合资源服务器的 jti 黑名单检查）。
            // TODO: 后续版本增强 JWT 的 jti 支持
        }

        log.info("OAuth2 token revoked: tokenTypeHint={}, deleted={}",
                tokenTypeHint, deleted);
    }

    // ---- 私有辅助方法 ----

    private OAuthClient authenticate(String clientId, String clientSecret) {
        if (clientId == null || clientId.isEmpty()) {
            throw new InvalidClientException("missing client_id");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new InvalidClientException("missing client_secret");
        }
        OAuthClient client = clientDetailsService.authenticateClient(clientId, clientSecret);
        if (client == null) {
            throw new InvalidClientException("client authentication failed");
        }
        return client;
    }

    private void storeRefreshToken(String refreshToken, String clientId,
                                    String globalIdHex, String scope) {
        Map<String, String> data = Map.of(
                "clientId", clientId,
                "globalId", globalIdHex,
                "scope", scope != null ? scope : "read"
        );
        redissonClient.getBucket(REFRESH_KEY_PREFIX + refreshToken)
                .set(com.alibaba.fastjson2.JSON.toJSONString(data),
                        Duration.ofSeconds(oAuth2Properties.getRefreshTokenTtl()));
    }
}
