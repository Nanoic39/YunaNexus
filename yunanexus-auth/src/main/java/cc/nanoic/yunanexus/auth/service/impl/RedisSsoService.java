package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.SecurityProperties;
import cc.nanoic.yunanexus.auth.domain.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.domain.TokenExchangeRequest;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import cc.nanoic.yunanexus.auth.service.AuthService;
import cc.nanoic.yunanexus.auth.service.AuthenticationService;
import cc.nanoic.yunanexus.auth.service.SsoService;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class RedisSsoService implements SsoService {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private cc.nanoic.yunanexus.auth.service.OAuthApplicationService applications;
    private boolean redirectAllowed(AuthClient client,String uri){return client.getClientCode().startsWith("oauth_")?applications!=null&&applications.redirectAllowed(client.getClientCode(),uri):client.getRedirectUri().equals(uri);}

    private static final String ROOT_PREFIX = "auth:sso_root:";
    private static final String CODE_PREFIX = "auth:sso_code:";
    private static final int ROOT_SECRET_BYTES = 32;
    private static final int CODE_BYTES = 32;
    private static final Pattern COOKIE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern STATE_PATTERN = Pattern.compile("^[A-Za-z0-9-._~]{43,256}$");
    private static final Pattern CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9-_]{43}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9-_]{43}$");
    private static final Pattern VERIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9-._~]{43,128}$");

    private static final String LUA_SET_SCRIPT = 
        "local t = redis.call('TIME') " +
        "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000) " +
        "if tonumber(ARGV[2]) <= now then return -1 end " +
        "local result = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[3]) " +
        "if result then redis.call('PEXPIREAT', KEYS[1], ARGV[2]) return 1 else return 0 end";

    private static final String LUA_CONSUME_SCRIPT = 
        "local t = redis.call('TIME') " +
        "local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000) " +
        "local current = redis.call('GET', KEYS[1]) " +
        "if current == ARGV[1] then " +
        "  local expire = tonumber(ARGV[2]) " +
        "  if expire > now then " +
        "    redis.call('DEL', KEYS[1]) " +
        "    return 1 " +
        "  else " +
        "    redis.call('DEL', KEYS[1]) " +
        "    return -1 " +
        "  end " +
        "else " +
        "  return 0 " +
        "end";

    private static final DefaultRedisScript<Long> SET_SCRIPT = new DefaultRedisScript<>(LUA_SET_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(LUA_CONSUME_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthRedisKeys authRedisKeys;
    private final SecurityProperties securityProperties;
    private final AuthenticationService authenticationService;
    private final AuthService authService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RedisSsoService(
            StringRedisTemplate redisTemplate,
            @Qualifier("authSessionObjectMapper") ObjectMapper objectMapper,
            AuthRedisKeys authRedisKeys,
            SecurityProperties securityProperties,
            AuthenticationService authenticationService,
            AuthService authService,
            Clock clock) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authRedisKeys = authRedisKeys;
        this.securityProperties = securityProperties;
        this.authenticationService = authenticationService;
        this.authService = authService;
        this.clock = clock;
    }

    @Override
    public void establish(LoginResult result, HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedIdentity identity = authenticationService.authenticateAccess(result.getAccessToken());
        SessionInfo session = authenticationService.requireSession(identity.sid());

        byte[] secretBytes = new byte[ROOT_SECRET_BYTES];
        secureRandom.nextBytes(secretBytes);
        String rootSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String rootHash = sha256Hex(rootSecret);

        String oldCookieValue = readCookie(request);
        if (oldCookieValue != null && !oldCookieValue.isEmpty()) {
            String oldHash = sha256Hex(oldCookieValue);
            redisTemplate.delete(authRedisKeys.ssoRoot(oldHash));
        }

        long nowMillis = clock.millis();
        long sessionExpireAt = session.getExpireAtMillis();
        if (sessionExpireAt <= nowMillis) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND, "会话已过期");
        }
        long ttlMillis = sessionExpireAt - nowMillis;
        long cookieMaxAgeSeconds = ttlMillis / 1000;
        if (cookieMaxAgeSeconds < 1) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND, "会话剩余时间不足");
        }

        RootRecord rootRecord = new RootRecord(
            session.getSid(),
            String.valueOf(session.getUserId()),
            session.getAccountEpoch(),
            sessionExpireAt
        );

        String rootKey = authRedisKeys.ssoRoot(rootHash);
        try {
            String rootJson = objectMapper.writeValueAsString(rootRecord);
            Long setResult = redisTemplate.execute(SET_SCRIPT, List.of(rootKey), rootJson, String.valueOf(sessionExpireAt), String.valueOf(ttlMillis));

            if (setResult == null || setResult == -1) {
                throw new BizException(ErrorCode.SESSION_NOT_FOUND, "会话已过期");
            }
            if (setResult == 0) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "SSO 根凭证创建冲突");
            }

            try {
                SessionInfo recheckedSession = authenticationService.requireSession(session.getSid());
                if (!session.getAccountEpoch().equals(recheckedSession.getAccountEpoch())) {
                    throw new BizException(ErrorCode.TOKEN_REVOKED, "账号纪元已变更");
                }
            } catch (Exception e) {
                redisTemplate.delete(rootKey);
                throw e;
            }

            Cookie cookie = new Cookie(securityProperties.getSsoCookieName(), rootSecret);
            cookie.setHttpOnly(true);
            cookie.setSecure(securityProperties.isSecureCookie());
            cookie.setPath("/");
            cookie.setMaxAge((int) cookieMaxAgeSeconds);
            cookie.setAttribute("SameSite", "Lax");
            response.addCookie(cookie);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "SSO 根凭证创建失败");
        }
    }

    @Override
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = readCookie(request);
        if (cookieValue != null && !cookieValue.isEmpty()) {
            String rootHash = sha256Hex(cookieValue);
            redisTemplate.delete(authRedisKeys.ssoRoot(rootHash));
        }
        Cookie cookie = new Cookie(securityProperties.getSsoCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setSecure(securityProperties.isSecureCookie());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    @Override
    public String authorize(AuthorizeRequest request, String cookieValue) {
        if (cookieValue == null || cookieValue.isEmpty()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO Cookie 缺失");
        }
        validateCookieFormat(cookieValue);
        validateAuthorizeRequest(request);

        String rootHash = sha256Hex(cookieValue);
        String rootKey = authRedisKeys.ssoRoot(rootHash);
        String rootJson = redisTemplate.opsForValue().get(rootKey);
        if (rootJson == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话不存在");
        }

        RootRecord rootRecord;
        try {
            rootRecord = objectMapper.readValue(rootJson, RootRecord.class);
        } catch (Exception e) {
            throw new IllegalStateException("SSO 根凭证数据损坏");
        }

        long nowMillis = clock.millis();
        if (rootRecord.expireAtMillis() <= nowMillis) {
            redisTemplate.delete(rootKey);
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话已过期");
        }

        long userId;
        try {
            userId = Long.parseLong(rootRecord.userId());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("SSO 根凭证数据损坏");
        }

        SessionInfo session = authenticationService.requireSession(rootRecord.sourceSid());
        if (session.getUserId() != userId) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话身份不匹配");
        }
        if (!session.getAccountEpoch().equals(rootRecord.accountEpoch())) {
            throw new BizException(ErrorCode.TOKEN_REVOKED, "账号纪元已变更");
        }
        if (rootRecord.expireAtMillis() > session.getExpireAtMillis()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 根凭证超出会话有效期");
        }

        AuthClient client = authenticationService.requireGrant(session, request.getClientCode());

        if (!redirectAllowed(client,request.getRedirectUri())) {
            throw new BizException(ErrorCode.FORBIDDEN, "redirectUri 不匹配");
        }

        validateRedirectUri(request.getRedirectUri());

        byte[] codeBytes = new byte[CODE_BYTES];
        secureRandom.nextBytes(codeBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes);
        String codeHash = sha256Hex(code);

        long codeTtlMillis = Math.min(
            (long) securityProperties.getCodeTtlSeconds() * 1000,
            rootRecord.expireAtMillis() - nowMillis
        );
        if (codeTtlMillis < 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话剩余时间不足");
        }
        long codeExpireAt = nowMillis + codeTtlMillis;

        CodeRecord codeRecord = new CodeRecord(
            rootHash,
            rootRecord.sourceSid(),
            rootRecord.userId(),
            rootRecord.accountEpoch(),
            request.getClientCode(),
            request.getRedirectUri(),
            request.getCodeChallenge(),
            codeExpireAt
        );

        String codeKey = authRedisKeys.ssoCode(codeHash);
        try {
            String codeJson = objectMapper.writeValueAsString(codeRecord);
            Long setResult = redisTemplate.execute(SET_SCRIPT, List.of(codeKey), codeJson, String.valueOf(codeExpireAt), String.valueOf(codeTtlMillis));

            if (setResult == null || setResult == -1) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话已过期");
            }
            if (setResult == 0) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "授权码创建冲突");
            }

            try {
                String recheckedRootJson = redisTemplate.opsForValue().get(rootKey);
                if (recheckedRootJson == null) {
                    throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话已失效");
                }
                RootRecord recheckedRoot = objectMapper.readValue(recheckedRootJson, RootRecord.class);
                if (!recheckedRoot.accountEpoch().equals(rootRecord.accountEpoch())) {
                    throw new BizException(ErrorCode.TOKEN_REVOKED, "账号纪元已变更");
                }

                SessionInfo recheckedSession = authenticationService.requireSession(session.getSid());
                if (!recheckedSession.getAccountEpoch().equals(session.getAccountEpoch())) {
                    throw new BizException(ErrorCode.TOKEN_REVOKED, "账号纪元已变更");
                }
            } catch (Exception e) {
                redisTemplate.delete(codeKey);
                throw e;
            }

            return buildRedirectUrl(request.getRedirectUri(), code, request.getState());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "授权码创建失败");
        }
    }

    @Override
    public LoginResult exchange(TokenExchangeRequest request) {
        validateExchangeRequest(request);
        if(request.getClientCode().startsWith("oauth_")){
            if(applications==null)throw new BizException(ErrorCode.FORBIDDEN,"接入验证不可用");
            applications.verifySecret(request.getClientCode(),request.getClientSecret());
        }

        String codeHash = sha256Hex(request.getCode());
        String codeKey = authRedisKeys.ssoCode(codeHash);
        String codeJson = redisTemplate.opsForValue().get(codeKey);
        if (codeJson == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "授权码无效或已使用");
        }

        CodeRecord codeRecord;
        try {
            codeRecord = objectMapper.readValue(codeJson, CodeRecord.class);
        } catch (Exception e) {
            throw new IllegalStateException("授权码数据损坏");
        }

        long nowMillis = clock.millis();
        if (codeRecord.expiresAtMillis() <= nowMillis) {
            redisTemplate.delete(codeKey);
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "授权码已过期");
        }

        String expectedChallenge = sha256Base64Url(request.getCodeVerifier());
        if (!MessageDigest.isEqual(
                expectedChallenge.getBytes(StandardCharsets.US_ASCII),
                codeRecord.codeChallenge().getBytes(StandardCharsets.US_ASCII))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "PKCE 校验失败");
        }

        if (!codeRecord.clientCode().equals(request.getClientCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "clientCode 不匹配");
        }
        if (!codeRecord.redirectUri().equals(request.getRedirectUri())) {
            throw new BizException(ErrorCode.FORBIDDEN, "redirectUri 不匹配");
        }

        String rootKey = authRedisKeys.ssoRoot(codeRecord.rootHash());
        String rootJson = redisTemplate.opsForValue().get(rootKey);
        if (rootJson == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话已失效");
        }

        RootRecord rootRecord;
        try {
            rootRecord = objectMapper.readValue(rootJson, RootRecord.class);
        } catch (Exception e) {
            throw new IllegalStateException("SSO 根凭证数据损坏");
        }

        if (rootRecord.expireAtMillis() <= nowMillis) {
            redisTemplate.delete(rootKey);
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话已过期");
        }
        if (codeRecord.expiresAtMillis() > rootRecord.expireAtMillis()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "授权码超出根凭证有效期");
        }

        if (!rootRecord.sourceSid().equals(codeRecord.sourceSid())
            || !rootRecord.userId().equals(codeRecord.userId())
            || !rootRecord.accountEpoch().equals(codeRecord.accountEpoch())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话身份不匹配");
        }

        long userId;
        try {
            userId = Long.parseLong(codeRecord.userId());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("授权码数据损坏");
        }

        SessionInfo session = authenticationService.requireSession(codeRecord.sourceSid());
        if (rootRecord.expireAtMillis() > session.getExpireAtMillis()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 根凭证超出会话有效期");
        }
        if (session.getUserId() != userId) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO 会话身份不匹配");
        }
        if (!session.getAccountEpoch().equals(codeRecord.accountEpoch())) {
            throw new BizException(ErrorCode.TOKEN_REVOKED, "账号纪元已变更");
        }

        AuthClient client = authenticationService.requireGrant(session, codeRecord.clientCode());
        if (!redirectAllowed(client,codeRecord.redirectUri())) {
            throw new BizException(ErrorCode.FORBIDDEN, "redirectUri 不匹配");
        }
        validateRedirectUri(codeRecord.redirectUri());

        Long consumeResult = redisTemplate.execute(
            CONSUME_SCRIPT,
            List.of(codeKey),
            codeJson,
            String.valueOf(codeRecord.expiresAtMillis())
        );

        if (consumeResult == null || consumeResult == 0) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "授权码已被使用");
        }
        if (consumeResult == -1) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "授权码已过期");
        }

        return authService.issueForSession(session, codeRecord.clientCode());
    }

    @Override
    public String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String found = null;
        for (Cookie cookie : cookies) {
            if (securityProperties.getSsoCookieName().equals(cookie.getName())) {
                if (found != null) {
                    throw new BizException(ErrorCode.UNAUTHORIZED, "SSO Cookie 重复");
                }
                found = cookie.getValue();
            }
        }
        if (found != null) {
            validateCookieFormat(found);
        }
        return found;
    }

    private void validateCookieFormat(String value) {
        if (value == null || !COOKIE_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "SSO Cookie 格式无效");
        }
    }

    private void validateAuthorizeRequest(AuthorizeRequest request) {
        if (request.getState() == null || !STATE_PATTERN.matcher(request.getState()).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "state 格式无效");
        }
        if (request.getCodeChallenge() == null || !CHALLENGE_PATTERN.matcher(request.getCodeChallenge()).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "codeChallenge 格式无效");
        }
        if (request.getCodeChallengeMethod() == null || !"S256".equals(request.getCodeChallengeMethod())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅支持 S256");
        }
        if (request.getClientCode() == null || request.getClientCode().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "clientCode 不能为空");
        }
        if (request.getRedirectUri() == null || request.getRedirectUri().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 不能为空");
        }
    }

    private void validateExchangeRequest(TokenExchangeRequest request) {
        if (request.getCode() == null || !CODE_PATTERN.matcher(request.getCode()).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "code 格式无效");
        }
        if (request.getCodeVerifier() == null || !VERIFIER_PATTERN.matcher(request.getCodeVerifier()).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "codeVerifier 格式无效");
        }
        if (request.getClientCode() == null || request.getClientCode().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "clientCode 不能为空");
        }
        if (request.getRedirectUri() == null || request.getRedirectUri().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 不能为空");
        }
    }

    private void validateRedirectUri(String redirectUri) {
        if (redirectUri == null || redirectUri.length() > 500) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 无效");
        }
        if (redirectUri.contains("\r") || redirectUri.contains("\n")
            || redirectUri.contains("%0d") || redirectUri.contains("%0a")
            || redirectUri.contains("%0D") || redirectUri.contains("%0A")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 无效");
        }
        URI uri;
        try {
            uri = new URI(redirectUri);
        } catch (URISyntaxException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 无效");
        }

        if (uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 无效");
        }
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String param : rawQuery.split("&")) {
                String key = param.split("=")[0];
                String decodedKey = java.net.URLDecoder.decode(key, StandardCharsets.UTF_8);
                if ("code".equals(decodedKey) || "state".equals(decodedKey)) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 不能包含 code/state 参数");
                }
            }
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 无效");
        }
        scheme = scheme.toLowerCase();
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 仅支持 http/https");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 无效");
        }
        if ("http".equals(scheme)) {
            boolean isLoopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            if ((!securityProperties.isAllowLocalHttp() || !isLoopback) && !securityProperties.isTemporaryHttpOrigin(uri)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "redirectUri 不允许非回环 HTTP");
            }
        }
    }

    private String buildRedirectUrl(String redirectUri, String code, String state) {
        String separator = redirectUri.contains("?") ? "&" : "?";
        return redirectUri + separator + "code=" + urlEncode(code) + "&state=" + urlEncode(state);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("哈希计算失败");
        }
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("哈希计算失败");
        }
    }

    private record RootRecord(
        String sourceSid,
        String userId,
        String accountEpoch,
        long expireAtMillis
    ) {}

    private record CodeRecord(
        String rootHash,
        String sourceSid,
        String userId,
        String accountEpoch,
        String clientCode,
        String redirectUri,
        String codeChallenge,
        long expiresAtMillis
    ) {}
}
