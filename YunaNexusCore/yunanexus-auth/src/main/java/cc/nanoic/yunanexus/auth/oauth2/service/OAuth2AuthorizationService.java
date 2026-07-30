package cc.nanoic.yunanexus.auth.oauth2.service;

import cc.nanoic.yunanexus.auth.entity.OAuthClient;
import cc.nanoic.yunanexus.auth.oauth2.config.OAuth2Properties;
import cc.nanoic.yunanexus.auth.oauth2.exception.InvalidRequestException;
import cc.nanoic.yunanexus.auth.oauth2.model.AuthorizationDetailsVO;
import cc.nanoic.yunanexus.auth.oauth2.model.AuthorizationSession;
import cc.nanoic.yunanexus.auth.oauth2.validator.AuthorizationRequestValidator;
import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 授权流程编排服务.
 * <p>负责授权会话的创建、查询、批准和拒绝.</p>
 */
@Service
public class OAuth2AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthorizationService.class);

    private static final String SESSION_KEY_PREFIX = "oauth2:authz:";
    private static final String CODE_KEY_PREFIX = "oauth2:code:";

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private OAuth2Properties oAuth2Properties;

    @Resource
    private AuthorizationRequestValidator validator;

    @Resource
    private OAuth2ClientDetailsService clientDetailsService;

    /**
     * 创建授权会话 — 校验所有参数，将授权请求暂存到 Redis.
     *
     * @param clientId             客户端 UUID
     * @param redirectUri          回调地址
     * @param responseType         响应类型
     * @param scope                授权范围
     * @param state                客户端 state
     * @param codeChallenge        PKCE code_challenge
     * @param codeChallengeMethod  PKCE 方法
     * @return 授权会话
     */
    public AuthorizationSession createSession(String clientId, String redirectUri,
                                               String responseType, String scope,
                                               String state, String codeChallenge,
                                               String codeChallengeMethod) {

        // 校验所有参数（失败会自动抛 OAuth2Exception 并回跳 redirect_uri）
        OAuthClient client = validator.validate(
                clientId, redirectUri, responseType, scope, state,
                codeChallenge, codeChallengeMethod);

        // 确定最终 scope
        String finalScope = (scope != null && !scope.isEmpty()) ? scope : client.getScope();

        // 创建会话
        AuthorizationSession session = new AuthorizationSession();
        session.setSessionId(RandomUtil.randomString(32));
        session.setClientId(clientId);
        session.setClientName(client.getClientName());
        session.setRedirectUri(redirectUri);
        session.setScope(finalScope);
        session.setState(state);
        session.setCodeChallenge(codeChallenge);
        session.setCodeChallengeMethod(
                codeChallengeMethod != null ? codeChallengeMethod : "S256");
        session.setNonce(RandomUtil.randomString(16));
        session.setCreatedAt(System.currentTimeMillis() / 1000);

        // 存入 Redis
        String key = SESSION_KEY_PREFIX + session.getSessionId();
        redissonClient.getBucket(key).set(
                JSON.toJSONString(session),
                Duration.ofSeconds(oAuth2Properties.getAuthorizationSessionTtl()));

        log.info("OAuth2 authorization session created: sessionId={}, clientId={}, scope={}",
                session.getSessionId(), clientId, finalScope);

        return session;
    }

    /**
     * 获取授权会话详情（供前端授权页面展示）.
     *
     * @param sessionId 会话 ID
     * @return 授权详情，session 不存在或过期返回 null
     */
    public AuthorizationDetailsVO getSessionDetails(String sessionId) {
        AuthorizationSession session = loadSession(sessionId);
        if (session == null) {
            return null;
        }

        OAuthClient client = clientDetailsService.findActiveClient(session.getClientId());

        AuthorizationDetailsVO vo = new AuthorizationDetailsVO();
        vo.setClientName(session.getClientName());
        vo.setDescription(client != null ? client.getDescription() : null);
        vo.setScope(session.getScope());
        vo.setRedirectUri(session.getRedirectUri());
        vo.setLoggedIn(PermissionContext.hasIdentity());
        return vo;
    }

    /**
     * 用户批准授权 — 生成授权码并返回重定向 URL.
     *
     * @param sessionId 会话 ID
     * @return {code, state, redirectUri}
     */
    public Map<String, String> approve(String sessionId) {
        AuthorizationSession session = loadSession(sessionId);
        if (session == null) {
            throw new InvalidRequestException("authorization session expired or not found");
        }

        // 生成授权码（48 位安全随机字符串）
        String code = RandomUtil.randomString(48);

        // 将授权码关联数据存入 Redis（TTL 5 分钟）
        Map<String, Object> codeData = new HashMap<>();
        codeData.put("clientId", session.getClientId());
        codeData.put("globalId", HexUtil.encodeHexStr(PermissionContext.getGlobalId()));
        codeData.put("scope", session.getScope());
        codeData.put("redirectUri", session.getRedirectUri());
        codeData.put("state", session.getState());
        if (session.getCodeChallenge() != null) {
            codeData.put("codeChallenge", session.getCodeChallenge());
            codeData.put("codeChallengeMethod", session.getCodeChallengeMethod());
        }

        redissonClient.getBucket(CODE_KEY_PREFIX + code)
                .set(JSON.toJSONString(codeData),
                        Duration.ofSeconds(oAuth2Properties.getAuthorizationCodeTtl()));

        // 删除授权会话（一次性使用）
        deleteSession(sessionId);

        log.info("OAuth2 authorization code issued: clientId={}, userId={}, scope={}",
                session.getClientId(), codeData.get("globalId"), session.getScope());

        Map<String, String> result = new HashMap<>();
        result.put("code", code);
        result.put("state", session.getState());
        result.put("redirectUri", session.getRedirectUri());
        return result;
    }

    /**
     * 用户拒绝授权 — 返回错误重定向 URL.
     *
     * @param sessionId 会话 ID
     * @return {redirectUrl} 格式: redirectUri?error=access_denied&state=xxx
     */
    public Map<String, String> deny(String sessionId) {
        AuthorizationSession session = loadSession(sessionId);
        if (session == null) {
            throw new InvalidRequestException("authorization session expired or not found");
        }

        String redirectUri = session.getRedirectUri();
        StringBuilder sb = new StringBuilder(redirectUri);
        sb.append(redirectUri.contains("?") ? "&" : "?");
        sb.append("error=access_denied");
        if (session.getState() != null && !session.getState().isEmpty()) {
            sb.append("&state=").append(session.getState());
        }

        deleteSession(sessionId);

        log.info("OAuth2 authorization denied: clientId={}", session.getClientId());

        Map<String, String> result = new HashMap<>();
        result.put("redirectUrl", sb.toString());
        return result;
    }

    /**
     * 从 Redis 加载授权码关联数据并原子删除（保证一次性使用）.
     *
     * @param code 授权码
     * @return 授权码数据，不存在或已使用返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> consumeCode(String code) {
        RBucket<String> bucket = redissonClient.getBucket(CODE_KEY_PREFIX + code);
        String data = bucket.get();
        if (data == null) {
            return null;
        }
        // 原子删除 — 防止重放攻击
        bucket.delete();
        return JSON.parseObject(data, Map.class);
    }

    private AuthorizationSession loadSession(String sessionId) {
        RBucket<String> bucket = redissonClient.getBucket(SESSION_KEY_PREFIX + sessionId);
        String data = bucket.get();
        if (data == null) {
            return null;
        }
        return JSON.parseObject(data, AuthorizationSession.class);
    }

    private void deleteSession(String sessionId) {
        redissonClient.getBucket(SESSION_KEY_PREFIX + sessionId).delete();
    }
}
