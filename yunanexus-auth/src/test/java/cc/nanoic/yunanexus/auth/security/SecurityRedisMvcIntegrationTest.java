package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import cc.nanoic.yunanexus.auth.client.UserProfileSyncClient;
import cc.nanoic.yunanexus.auth.config.JwtProperties;
import cc.nanoic.yunanexus.auth.config.SecurityConfig;
import cc.nanoic.yunanexus.auth.config.SecurityProperties;
import cc.nanoic.yunanexus.auth.controller.AuthController;
import cc.nanoic.yunanexus.auth.controller.SsoController;
import cc.nanoic.yunanexus.auth.domain.AuthorizeRequest;
import cc.nanoic.yunanexus.auth.domain.LoginRequest;
import cc.nanoic.yunanexus.auth.domain.LoginResult;
import cc.nanoic.yunanexus.auth.domain.MeResponse;
import cc.nanoic.yunanexus.auth.domain.SessionInfo;
import cc.nanoic.yunanexus.auth.domain.TokenExchangeRequest;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthLoginAttemptMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
import cc.nanoic.yunanexus.auth.security.AuthSecurityExceptionHandler;
import cc.nanoic.yunanexus.auth.service.AccountEpochService;
import cc.nanoic.yunanexus.auth.service.AuthService;
import cc.nanoic.yunanexus.auth.service.AuthenticationService;
import cc.nanoic.yunanexus.auth.service.CaptchaService;
import cc.nanoic.yunanexus.auth.service.LoginAttemptService;
import cc.nanoic.yunanexus.auth.service.RegisterService;
import cc.nanoic.yunanexus.auth.service.SessionService;
import cc.nanoic.yunanexus.auth.service.SsoService;
import cc.nanoic.yunanexus.auth.service.TokenBlacklistService;
import cc.nanoic.yunanexus.auth.service.TokenService;
import cc.nanoic.yunanexus.auth.service.UnlockAppealService;
import cc.nanoic.yunanexus.auth.service.impl.AuthServiceImpl;
import cc.nanoic.yunanexus.auth.service.impl.DefaultAuthenticationService;
import cc.nanoic.yunanexus.auth.service.impl.JwtTokenService;
import cc.nanoic.yunanexus.auth.service.impl.RedisAccountEpochService;
import cc.nanoic.yunanexus.auth.service.impl.RedisSessionService;
import cc.nanoic.yunanexus.auth.service.impl.RedisSsoService;
import cc.nanoic.yunanexus.auth.service.impl.RedisTokenBlacklistService;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named = "yunanexus.redis.it", matches = "true")
public class SecurityRedisMvcIntegrationTest {

    private static final String TEST_PREFIX = "auth-it-" + UUID.randomUUID().toString();
    private static final String CLIENT_WEB = "clientAweb";
    private static final String CLIENT_ADMIN = "clientBadmin";
    private static final String REDIRECT_WEB = "http://localhost:8100/callback";
    private static final String REDIRECT_ADMIN = "http://localhost:8100/admin/callback";
    private static final String TEST_USERNAME = "alice";
    private static final String TEST_PASSWORD = "test-only-password";
    private static final String TEST_PASSWORD_HASH = new BCryptPasswordEncoder().encode(TEST_PASSWORD);
    private static final long TEST_USER_ID = 10001L;
    private static final String TEST_EXTERNAL_ID = "12345678901234567890123456789";

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private StringRedisTemplate redisTemplate;
    private LettuceConnectionFactory redisFactory;
    private AuthProperties authProperties;
    private SecurityProperties securityProperties;
    private AuthRedisKeys authRedisKeys;
    private Clock clock;

    private AuthUserMapper authUserMapper;
    private AuthClientMapper authClientMapper;
    private AuthPlatformMapper authPlatformMapper;
    private AuthLoginAttemptMapper loginAttemptMapper;
    private LoginAttemptService loginAttemptService;
    private CaptchaService captchaService;
    private RegisterService registerService;
    private UnlockAppealService unlockAppealService;
    private TokenBlacklistService tokenBlacklistService;
    private AuthService authService;
    private AuthenticationService authenticationService;
    private SsoService ssoService;
    private SessionService sessionService;
    private AccountEpochService accountEpochService;
    private TokenService tokenService;
    private ObjectMapper objectMapper;

    private AuthUser aliceUser;
    private AuthClient clientWeb;
    private AuthClient clientAdmin;
    private AuthPlatform platformWeb;
    private AuthPlatform platformAdmin;

    @BeforeAll
    void setUpAll() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("local");
        Map<String, Object> props = new HashMap<>();
        props.put("yunanexus.auth.security.public-origin", "http://localhost:8100");
        props.put("yunanexus.auth.security.allow-local-http", true);
        props.put("yunanexus.auth.security.cors-allowed-origins", "http://localhost:5173");
        props.put("yunanexus.auth.security.code-ttl-seconds", 2);
        props.put("yunanexus.auth.redis-key-prefix", TEST_PREFIX);
        props.put("yunanexus.auth.redis.redis-key-prefix", TEST_PREFIX);
        props.put("yunanexus.auth.jwt.active-kid", "test-v1");
        env.getPropertySources().addFirst(new MapPropertySource("testProps", props));
        context.setEnvironment(env);
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();

        redisFactory = context.getBean(LettuceConnectionFactory.class);
        redisTemplate = context.getBean(StringRedisTemplate.class);
        redisTemplate.execute((RedisCallback<String>) connection -> {
            String pong = connection.ping();
            assertEquals("PONG", pong);
            return pong;
        });

        authProperties = context.getBean(AuthProperties.class);
        securityProperties = context.getBean(SecurityProperties.class);
        authRedisKeys = context.getBean(AuthRedisKeys.class);
        clock = context.getBean(Clock.class);
        objectMapper = context.getBean("authSessionObjectMapper", ObjectMapper.class);

        authUserMapper = context.getBean(AuthUserMapper.class);
        authClientMapper = context.getBean(AuthClientMapper.class);
        authPlatformMapper = context.getBean(AuthPlatformMapper.class);
        loginAttemptMapper = context.getBean(AuthLoginAttemptMapper.class);
        loginAttemptService = context.getBean(LoginAttemptService.class);
        captchaService = context.getBean(CaptchaService.class);
        registerService = context.getBean(RegisterService.class);
        unlockAppealService = context.getBean(UnlockAppealService.class);
        tokenBlacklistService = context.getBean(TokenBlacklistService.class);
        authService = context.getBean(AuthService.class);
        authenticationService = context.getBean(AuthenticationService.class);
        ssoService = context.getBean(SsoService.class);
        sessionService = context.getBean(SessionService.class);
        accountEpochService = context.getBean(AccountEpochService.class);
        tokenService = context.getBean(TokenService.class);

        mockMvc = MockMvcBuilders
                .webAppContextSetup((WebApplicationContext) context)
                .apply(springSecurity())
                .build();

        setupMocks();
    }

    private void setupMocks() {
        aliceUser = new AuthUser();
        aliceUser.setUserId(TEST_USER_ID);
        aliceUser.setExternalId(TEST_EXTERNAL_ID);
        aliceUser.setUsername(TEST_USERNAME);
        aliceUser.setPassword(TEST_PASSWORD_HASH);
        aliceUser.setStatus(1);
        aliceUser.setFailCount(0);

        clientWeb = new AuthClient();
        clientWeb.setClientCode(CLIENT_WEB);
        clientWeb.setRedirectUri(REDIRECT_WEB);
        clientWeb.setStatus(1);

        clientAdmin = new AuthClient();
        clientAdmin.setClientCode(CLIENT_ADMIN);
        clientAdmin.setRedirectUri(REDIRECT_ADMIN);
        clientAdmin.setStatus(1);

        platformWeb = new AuthPlatform();
        platformWeb.setUserId(TEST_USER_ID);
        platformWeb.setClientCode(CLIENT_WEB);
        platformWeb.setStatus(1);

        platformAdmin = new AuthPlatform();
        platformAdmin.setUserId(TEST_USER_ID);
        platformAdmin.setClientCode(CLIENT_ADMIN);
        platformAdmin.setStatus(1);
    }

    @BeforeEach
    void resetMocks() {
        setupMocks();
        Mockito.reset(authUserMapper, authClientMapper, authPlatformMapper, loginAttemptMapper,
                loginAttemptService, captchaService, registerService, unlockAppealService);

        when(authUserMapper.selectByUsername(TEST_USERNAME)).thenReturn(aliceUser);
        when(authUserMapper.selectByEmail(anyString())).thenReturn(null);
        when(authUserMapper.selectByMobile(anyString(), anyString())).thenReturn(null);
        when(authUserMapper.resetFailCountOnLoginSuccess(eq(TEST_USERNAME), any(), anyString())).thenReturn(1);
        when(authUserMapper.incrementFailCount(eq(TEST_USERNAME), any())).thenReturn(1);
        when(authUserMapper.updateStatusAndLock(anyLong(), anyInt(), any(), any())).thenReturn(1);

        when(authClientMapper.selectByClientCode(CLIENT_WEB)).thenReturn(clientWeb);
        when(authClientMapper.selectByClientCode(CLIENT_ADMIN)).thenReturn(clientAdmin);
        when(authClientMapper.selectByClientCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            if (CLIENT_WEB.equals(code)) return clientWeb;
            if (CLIENT_ADMIN.equals(code)) return clientAdmin;
            return null;
        });

        when(authPlatformMapper.selectByUserIdAndClient(TEST_USER_ID, CLIENT_WEB)).thenReturn(platformWeb);
        when(authPlatformMapper.selectByUserIdAndClient(TEST_USER_ID, CLIENT_ADMIN)).thenReturn(platformAdmin);
        when(authPlatformMapper.selectByUserIdAndClient(anyLong(), anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(1);
            if (CLIENT_WEB.equals(code)) return platformWeb;
            if (CLIENT_ADMIN.equals(code)) return platformAdmin;
            return null;
        });

        when(loginAttemptService.captchaRequired(anyString())).thenReturn(false);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
        when(loginAttemptService.recordFailure(anyString())).thenReturn(1);
        when(loginAttemptService.lock(anyString())).thenReturn(System.currentTimeMillis() + 1800000);
        doNothing().when(loginAttemptService).reset(anyString());

        when(captchaService.verify(anyString(), anyString())).thenReturn(true);

        when(loginAttemptMapper.insert(any(cc.nanoic.yunanexus.auth.entity.AuthLoginAttempt.class))).thenReturn(1);


    }

    @AfterEach
    void cleanupRedis() {
        String prefix = TEST_PREFIX + ":";
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try (var cursor = connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(100)
                    .build())) {
                while (cursor.hasNext()) {
                    byte[] key = cursor.next();
                    String keyStr = new String(key, StandardCharsets.UTF_8);
                    assertTrue(keyStr.startsWith(prefix), "Key " + keyStr + " does not start with expected prefix " + prefix);
                    connection.del(key);
                }
            }
            return null;
        });
    }

    @AfterAll
    void tearDownAll() {
        try {
            cleanupRedis();
        } finally {
            if (context != null) {
                context.close();
            }
            if (redisFactory != null) {
                redisFactory.destroy();
            }
        }
    }

    // Helper record for CSRF
    private record CsrfInfo(String rawToken, String cookieValue) {}

    // Helper methods
    private CsrfInfo bootstrapCsrf() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String rawToken = json.get("data").get("token").asText();
        String headerName = json.get("data").get("headerName").asText();
        assertEquals("X-XSRF-TOKEN", headerName);

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("HttpOnly"), "Missing HttpOnly in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));
        // Spring's mock response omits generic Servlet Cookie attributes from the header.
        // Verify the actual Servlet 6 cookie attribute rather than weakening SameSite coverage.
        Cookie csrfCookie = result.getResponse().getCookie("YunaCsrf_LOCAL");
        assertNotNull(csrfCookie);
        assertEquals("Lax", csrfCookie.getAttribute("SameSite"));
        assertTrue(setCookie.contains("Path=/"), "Missing Path=/ in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));
        assertTrue(setCookie.contains("YunaCsrf_LOCAL"), "Missing YunaCsrf_LOCAL in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));

        String cookieValue = extractCookieValue(setCookie, "YunaCsrf_LOCAL");
        assertNotNull(cookieValue);
        assertNotEquals(rawToken, cookieValue);
        assertFalse(setCookie.contains("JSESSIONID"));

        return new CsrfInfo(rawToken, cookieValue);
    }

    private String extractCookieValue(String setCookie, String cookieName) {
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                return trimmed.substring(cookieName.length() + 1);
            }
        }
        return null;
    }

    private LoginResult loginWithCsrf(String username, String password, String clientCode, CsrfInfo csrf) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setClientCode(clientCode);

        String requestJson = objectMapper.writeValueAsString(request);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("YunaSSO_LOCAL"), "Missing YunaSSO_LOCAL in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));
        assertTrue(setCookie.contains("HttpOnly"), "Missing HttpOnly in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));
        assertNotNull(result.getResponse().getCookie("YunaSSO_LOCAL"));
        assertEquals("Lax", result.getResponse().getCookie("YunaSSO_LOCAL").getAttribute("SameSite"));
        assertTrue(setCookie.contains("Path=/"), "Missing Path=/ in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));

        String ssoCookie = extractCookieValue(setCookie, "YunaSSO_LOCAL");
        assertNotNull(ssoCookie);
        assertEquals(43, ssoCookie.length());

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode data = json.get("data");
        String accessToken = data.get("accessToken").asText();
        String refreshToken = data.get("refreshToken").asText();
        String externalId = data.get("externalId").asText();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
        assertEquals(TEST_EXTERNAL_ID, externalId);

        return new LoginResult(accessToken, refreshToken, authProperties.getAccessTokenTtlSeconds(), externalId);
    }

    private String getSsoCookieFromResponse(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        return extractCookieValue(setCookie, "YunaSSO_LOCAL");
    }

    private Map<String, String> authorizeWithRootCookie(String ssoCookie, String clientCode, 
                                                         String redirectUri, String state, 
                                                         String verifier, CsrfInfo csrf) throws Exception {
        String challenge = sha256Base64Url(verifier);

        AuthorizeRequest authReq = new AuthorizeRequest();
        authReq.setState(state);
        authReq.setCodeChallenge(challenge);
        authReq.setCodeChallengeMethod("S256");
        authReq.setClientCode(clientCode);
        authReq.setRedirectUri(redirectUri);

        String requestJson = objectMapper.writeValueAsString(authReq);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        String redirectUrl = json.get("data").get("redirectUrl").asText();

        Map<String, String> params = parseRedirectUrl(redirectUrl);
        assertTrue(params.containsKey("code"));
        assertTrue(params.containsKey("state"));
        assertEquals(state, params.get("state"));
        assertFalse(params.containsKey("access_token"));
        assertFalse(params.containsKey("refresh_token"));

        return params;
    }

    private Map<String, String> parseRedirectUrl(String redirectUrl) throws Exception {
        Map<String, String> params = new HashMap<>();
        String query = redirectUrl.substring(redirectUrl.indexOf("?") + 1);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return params;
    }

    private LoginResult exchangeCode(String code, String clientCode, String redirectUri, String verifier) throws Exception {
        TokenExchangeRequest exchangeReq = new TokenExchangeRequest();
        exchangeReq.setCode(code);
        exchangeReq.setCodeVerifier(verifier);
        exchangeReq.setClientCode(clientCode);
        exchangeReq.setRedirectUri(redirectUri);

        String requestJson = objectMapper.writeValueAsString(exchangeReq);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode data = json.get("data");
        return new LoginResult(
                data.get("accessToken").asText(),
                data.get("refreshToken").asText(),
                data.get("expiresIn").asLong(),
                data.get("externalId").asText()
        );
    }

    private void meWithToken(String accessToken) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.externalId").value(TEST_EXTERNAL_ID))
                .andExpect(jsonPath("$.data.clientCode").exists())
                .andExpect(jsonPath("$.data.uid").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.epoch").doesNotExist());
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
            throw new RuntimeException(e);
        }
    }

    @Test
    void test1_bootstrapCsrfToken() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        assertNotNull(csrf.rawToken());
        assertNotNull(csrf.cookieValue());
        assertNotEquals(csrf.rawToken(), csrf.cookieValue());
    }

    @Test
    void test2_loginCsrfValidation() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);
        request.setClientCode(CLIENT_WEB);
        String requestJson = objectMapper.writeValueAsString(request);

        // No CSRF
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isForbidden());

        // Wrong CSRF
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("X-XSRF-TOKEN", "wrong-token")
                        .cookie(new Cookie("YunaCsrf_LOCAL", "wrong-cookie")))
                .andExpect(status().isForbidden());

        // Correct CSRF
        CsrfInfo csrf = bootstrapCsrf();
        LoginResult loginResult = loginWithCsrf(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB, csrf);
        assertNotNull(loginResult.getAccessToken());
        assertNotNull(loginResult.getRefreshToken());
    }

    @Test
    void test3_meEndpointAuthentication() throws Exception {
        // Unauthorized
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        // Invalid JWT
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());

        // Refresh token as Bearer
        CsrfInfo csrf = bootstrapCsrf();
        LoginResult loginResult = loginWithCsrf(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB, csrf);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + loginResult.getRefreshToken()))
                .andExpect(status().isUnauthorized());

        // Valid access token
        meWithToken(loginResult.getAccessToken());
    }

    @Test
    void test4_csrfRequiredOnProtectedEndpoints() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        LoginResult loginResult = loginWithCsrf(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB, csrf);
        String accessToken = loginResult.getAccessToken();

        // Logout without CSRF
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        // Logout-all without CSRF
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());

        // Refresh without CSRF
        cc.nanoic.yunanexus.auth.domain.RefreshRequest refreshReq = new cc.nanoic.yunanexus.auth.domain.RefreshRequest();
        refreshReq.setRefreshToken(loginResult.getRefreshToken());
        String refreshJson = objectMapper.writeValueAsString(refreshReq);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson))
                .andExpect(status().isForbidden());

        // Authorize without CSRF
        String state = generateState();
        String verifier = generateVerifier();
        String challenge = sha256Base64Url(verifier);
        AuthorizeRequest authReq = new AuthorizeRequest();
        authReq.setState(state);
        authReq.setCodeChallenge(challenge);
        authReq.setCodeChallengeMethod("S256");
        authReq.setClientCode(CLIENT_ADMIN);
        authReq.setRedirectUri(REDIRECT_ADMIN);
        String authJson = objectMapper.writeValueAsString(authReq);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson)
                        .cookie(new Cookie("YunaSSO_LOCAL", "dummycookie")))
                .andExpect(status().isForbidden());
    }

    @Test
    void test5_corsValidation() throws Exception {
        // Valid preflight
        mockMvc.perform(MockMvcRequestBuilders.options("/api/v1/auth/csrf")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        // Evil origin
        mockMvc.perform(MockMvcRequestBuilders.options("/api/v1/auth/csrf")
                        .header("Origin", "http://evil.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));

        // Wildcard origin
        mockMvc.perform(MockMvcRequestBuilders.options("/api/v1/auth/csrf")
                        .header("Origin", "*")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void test6_fullSsoCodeFlow() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        LoginResult webLogin = loginWithCsrf(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB, csrf);
        String ssoCookie = getSsoCookieFromResponse(null);

        // Need to get the cookie from the login response
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        ssoCookie = getSsoCookieFromResponse(loginResult);

        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(ssoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);

        LoginResult adminLogin = exchangeCode(authParams.get("code"), CLIENT_ADMIN, REDIRECT_ADMIN, verifier);
        assertNotNull(adminLogin.getAccessToken());
        assertNotNull(adminLogin.getRefreshToken());
        assertEquals(TEST_EXTERNAL_ID, adminLogin.getExternalId());

        meWithToken(adminLogin.getAccessToken());
    }

    private LoginRequest createLoginRequest(String username, String password, String clientCode) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setClientCode(clientCode);
        return request;
    }

    @Test
    void test7_redirectUriValidation() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);

        String state = generateState();
        String verifier = generateVerifier();
        String challenge = sha256Base64Url(verifier);

        // Mismatched redirect
        AuthorizeRequest authReq = new AuthorizeRequest();
        authReq.setState(state);
        authReq.setCodeChallenge(challenge);
        authReq.setCodeChallengeMethod("S256");
        authReq.setClientCode(CLIENT_ADMIN);
        authReq.setRedirectUri("http://localhost:8100/wrong-callback");
        String authJson = objectMapper.writeValueAsString(authReq);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson)
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isForbidden());

        // Open redirect attempt
        authReq.setRedirectUri("http://evil.com/callback");
        authJson = objectMapper.writeValueAsString(authReq);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson)
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void test8_pkceValidation() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);

        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(ssoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);
        String code = authParams.get("code");

        // Bad PKCE proof
        String badVerifier = generateVerifier();
        TokenExchangeRequest badExchange = new TokenExchangeRequest();
        badExchange.setCode(code);
        badExchange.setCodeVerifier(badVerifier);
        badExchange.setClientCode(CLIENT_ADMIN);
        badExchange.setRedirectUri(REDIRECT_ADMIN);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badExchange)))
                .andExpect(status().isUnauthorized());

        // Good PKCE still works
        LoginResult adminLogin = exchangeCode(code, CLIENT_ADMIN, REDIRECT_ADMIN, verifier);
        assertNotNull(adminLogin.getAccessToken());

        // Replay
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badExchange)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void test9_concurrentCodeExchange() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);

        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(ssoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);
        String code = authParams.get("code");

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    TokenExchangeRequest exchangeReq = new TokenExchangeRequest();
                    exchangeReq.setCode(code);
                    exchangeReq.setCodeVerifier(verifier);
                    exchangeReq.setClientCode(CLIENT_ADMIN);
                    exchangeReq.setRedirectUri(REDIRECT_ADMIN);
                    String requestJson = objectMapper.writeValueAsString(exchangeReq);

                    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson))
                            .andReturn();

                    if (result.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else if (result.getResponse().getStatus() == 401) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(threadCount - 1, failureCount.get());
    }

    @Test
    void test10_stateAndChallengeValidation() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);

        String verifier = generateVerifier();
        String challenge = sha256Base64Url(verifier);

        // Bad state length
        AuthorizeRequest authReq = new AuthorizeRequest();
        authReq.setState("short-state");
        authReq.setCodeChallenge(challenge);
        authReq.setCodeChallengeMethod("S256");
        authReq.setClientCode(CLIENT_ADMIN);
        authReq.setRedirectUri(REDIRECT_ADMIN);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isBadRequest());

        // Bad challenge
        authReq.setState(generateState());
        authReq.setCodeChallenge("bad-challenge");
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void test11_codeExpiry() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);

        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(ssoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);
        String code = authParams.get("code");

        // Check TTL
        String codeHash = sha256Hex(code);
        String codeKey = authRedisKeys.ssoCode(codeHash);
        Long ttl = redisTemplate.getExpire(codeKey);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 2);

        // Wait for expiry
        Thread.sleep(2500);

        TokenExchangeRequest exchangeReq = new TokenExchangeRequest();
        exchangeReq.setCode(code);
        exchangeReq.setCodeVerifier(verifier);
        exchangeReq.setClientCode(CLIENT_ADMIN);
        exchangeReq.setRedirectUri(REDIRECT_ADMIN);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void test12_globalLogout() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();

        // Login web platform
        MvcResult webLoginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String webSsoCookie = getSsoCookieFromResponse(webLoginResult);
        String webAccessToken = getAccessTokenFromResponse(webLoginResult);
        String webRefreshToken = getRefreshTokenFromResponse(webLoginResult);

        // Authorize admin code
        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(webSsoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);
        String code = authParams.get("code");

        // Exchange admin token
        LoginResult adminLogin = exchangeCode(code, CLIENT_ADMIN, REDIRECT_ADMIN, verifier);
        String adminAccessToken = adminLogin.getAccessToken();
        String adminRefreshToken = adminLogin.getRefreshToken();

        // Both tokens valid
        meWithToken(webAccessToken);
        meWithToken(adminAccessToken);

        // Global logout
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + webAccessToken)
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));

        // Both tokens invalid
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + webAccessToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + adminAccessToken))
                .andExpect(status().isUnauthorized());

        // Old refresh token invalid
        cc.nanoic.yunanexus.auth.domain.RefreshRequest refreshReq = new cc.nanoic.yunanexus.auth.domain.RefreshRequest();
        refreshReq.setRefreshToken(webRefreshToken);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isUnauthorized());

        // Old root cookie invalid
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAuthorizeRequest(generateState(), generateVerifier(), CLIENT_ADMIN, REDIRECT_ADMIN)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", webSsoCookie)))
                .andExpect(status().isUnauthorized());

        // Old code invalid
        TokenExchangeRequest exchangeReq = new TokenExchangeRequest();
        exchangeReq.setCode(code);
        exchangeReq.setCodeVerifier(verifier);
        exchangeReq.setClientCode(CLIENT_ADMIN);
        exchangeReq.setRedirectUri(REDIRECT_ADMIN);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeReq)))
                .andExpect(status().isUnauthorized());

        // New login works
        MvcResult newLoginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String newAccessToken = getAccessTokenFromResponse(newLoginResult);
        meWithToken(newAccessToken);
    }

    private AuthorizeRequest createAuthorizeRequest(String state, String verifier, String clientCode, String redirectUri) {
        AuthorizeRequest authReq = new AuthorizeRequest();
        authReq.setState(state);
        authReq.setCodeChallenge(sha256Base64Url(verifier));
        authReq.setCodeChallengeMethod("S256");
        authReq.setClientCode(clientCode);
        authReq.setRedirectUri(redirectUri);
        return authReq;
    }

    private String getAccessTokenFromResponse(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        return json.get("data").get("accessToken").asText();
    }

    private String getRefreshTokenFromResponse(MvcResult result) throws Exception {
        String responseBody = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(responseBody);
        return json.get("data").get("refreshToken").asText();
    }

    @Test
    void test13_clientDisabledOrRevoked() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);
        String accessToken = getAccessTokenFromResponse(loginResult);

        // Disable client
        clientAdmin.setStatus(0);
        String state = generateState();
        String verifier = generateVerifier();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAuthorizeRequest(state, verifier, CLIENT_ADMIN, REDIRECT_ADMIN)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isForbidden());

        // Revoke grant
        clientAdmin.setStatus(1);
        platformAdmin.setStatus(0);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAuthorizeRequest(state, verifier, CLIENT_ADMIN, REDIRECT_ADMIN)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isForbidden());

        // Disable user
        platformAdmin.setStatus(1);
        aliceUser.setStatus(0);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void test14_rootClearRemovesAllCookies() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);
        String accessToken = getAccessTokenFromResponse(loginResult);

        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(ssoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);
        String code = authParams.get("code");

        // Logout (clear root)
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));

        // Old root cookie invalid
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAuthorizeRequest(generateState(), generateVerifier(), CLIENT_ADMIN, REDIRECT_ADMIN)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isUnauthorized());

        // Old code invalid
        TokenExchangeRequest exchangeReq = new TokenExchangeRequest();
        exchangeReq.setCode(code);
        exchangeReq.setCodeVerifier(verifier);
        exchangeReq.setClientCode(CLIENT_ADMIN);
        exchangeReq.setRedirectUri(REDIRECT_ADMIN);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeReq)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void test15_tokenEndpointRejectsCookiesAndAuth() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);

        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(ssoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);
        String code = authParams.get("code");

        TokenExchangeRequest exchangeReq = new TokenExchangeRequest();
        exchangeReq.setCode(code);
        exchangeReq.setCodeVerifier(verifier);
        exchangeReq.setClientCode(CLIENT_ADMIN);
        exchangeReq.setRedirectUri(REDIRECT_ADMIN);
        String exchangeJson = objectMapper.writeValueAsString(exchangeReq);

        // With SSO cookie
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeJson)
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isUnauthorized());

        // With malformed SSO cookie
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeJson)
                        .cookie(new Cookie("YunaSSO_LOCAL", "malformed")))
                .andExpect(status().isUnauthorized());

        // With duplicate SSO cookies
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeJson)
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isUnauthorized());

        // With Authorization header
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeJson)
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void test16_spoofedXffUsesRemoteAddr() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .header("X-Forwarded-For", "1.2.3.4")
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk());

        verify(authUserMapper).resetFailCountOnLoginSuccess(eq(TEST_USERNAME), any(), eq("127.0.0.1"));
    }

    @Test
    void test17_securityHeadersAndUnknownPath() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/unknown"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void test18_corruptedRedisDataReturns503() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);
        String rootHash = sha256Hex(ssoCookie);
        String rootKey = authRedisKeys.ssoRoot(rootHash);

        // Corrupt root record
        redisTemplate.opsForValue().set(rootKey, "{invalid-json");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/sso/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createAuthorizeRequest(generateState(), generateVerifier(), CLIENT_ADMIN, REDIRECT_ADMIN)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()))
                        .cookie(new Cookie("YunaSSO_LOCAL", ssoCookie)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void test19_redisKeySecurityProperties() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult loginResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createLoginRequest(TEST_USERNAME, TEST_PASSWORD, CLIENT_WEB)))
                        .header("X-XSRF-TOKEN", csrf.rawToken())
                        .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue())))
                .andExpect(status().isOk())
                .andReturn();
        String ssoCookie = getSsoCookieFromResponse(loginResult);
        String rootHash = sha256Hex(ssoCookie);
        String rootKey = authRedisKeys.ssoRoot(rootHash);

        // Verify hashed key
        assertFalse(redisTemplate.hasKey(TEST_PREFIX + ":auth:sso_root:" + ssoCookie));
        assertTrue(redisTemplate.hasKey(rootKey));

        // Verify TTL
        Long rootTtl = redisTemplate.getExpire(rootKey);
        assertNotNull(rootTtl);
        assertTrue(rootTtl > 0);

        // Generate code and verify its TTL
        String state = generateState();
        String verifier = generateVerifier();
        Map<String, String> authParams = authorizeWithRootCookie(ssoCookie, CLIENT_ADMIN, REDIRECT_ADMIN, state, verifier, csrf);
        String code = authParams.get("code");
        String codeHash = sha256Hex(code);
        String codeKey = authRedisKeys.ssoCode(codeHash);
        Long codeTtl = redisTemplate.getExpire(codeKey);

        assertNotNull(codeTtl);
        assertTrue(codeTtl > 0);
        assertTrue(codeTtl <= rootTtl);
    }

    @Test
    void test20_localHttpCookieFlags() throws Exception {
        CsrfInfo csrf = bootstrapCsrf();
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertFalse(setCookie.contains("Secure"));
        assertNotNull(result.getResponse().getCookie("YunaCsrf_LOCAL"));
        assertEquals("Lax", result.getResponse().getCookie("YunaCsrf_LOCAL").getAttribute("SameSite"));
        assertTrue(setCookie.contains("HttpOnly"), "Missing HttpOnly in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));
        assertTrue(setCookie.contains("Path=/"), "Missing Path=/ in cookie attributes: " + setCookie.replaceFirst("=[^;]*", "=<redacted>"));
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthController.class, SsoController.class, AuthSecurityExceptionHandler.class})
    static class TestConfig {

        @Bean
        public Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
            template.afterPropertiesSet();
            return template;
        }

        @Bean
        public LettuceConnectionFactory lettuceConnectionFactory() {
            RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
            redisConfig.setHostName("localhost");
            redisConfig.setPort(6379);
            redisConfig.setDatabase(0);
            String redisPassword = System.getenv("REDIS_PASSWORD");
            if (redisPassword != null && !redisPassword.isEmpty()) {
                redisConfig.setPassword(RedisPassword.of(redisPassword));
            }
            LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        public AuthRedisKeys authRedisKeys(AuthProperties authProperties) {
            return new AuthRedisKeys(authProperties);
        }

        @Bean(name = "authSessionObjectMapper")
        public ObjectMapper authSessionObjectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthUserMapper authUserMapper() {
            return Mockito.mock(AuthUserMapper.class);
        }

        @Bean
        public AuthClientMapper authClientMapper() {
            return Mockito.mock(AuthClientMapper.class);
        }

        @Bean
        public AuthPlatformMapper authPlatformMapper() {
            return Mockito.mock(AuthPlatformMapper.class);
        }

        @Bean
        public AuthLoginAttemptMapper authLoginAttemptMapper() {
            return Mockito.mock(AuthLoginAttemptMapper.class);
        }

        @Bean
        public LoginAttemptService loginAttemptService() {
            return Mockito.mock(LoginAttemptService.class);
        }

        @Bean
        public CaptchaService captchaService() {
            return Mockito.mock(CaptchaService.class);
        }

        @Bean
        public RegisterService registerService() {
            return Mockito.mock(RegisterService.class);
        }

        @Bean
        public UnlockAppealService unlockAppealService() {
            return Mockito.mock(UnlockAppealService.class);
        }

        @Bean
        public TokenBlacklistService tokenBlacklistService() {
            return new RedisTokenBlacklistService(stringRedisTemplate(lettuceConnectionFactory()), authRedisKeys(authProperties()));
        }

        @Bean
        public SessionService sessionService() {
            return new RedisSessionService(stringRedisTemplate(lettuceConnectionFactory()), 
                    authSessionObjectMapper(), authRedisKeys(authProperties()));
        }

        @Bean
        public AccountEpochService accountEpochService() {
            return new RedisAccountEpochService(stringRedisTemplate(lettuceConnectionFactory()), 
                    authRedisKeys(authProperties()));
        }

        @Bean
        public TokenService tokenService() {
            JwtProperties jwtProperties = new JwtProperties();
            jwtProperties.setActiveKid("test-v1");
            List<JwtProperties.SigningKey> keys = new ArrayList<>();
            JwtProperties.SigningKey signingKey = new JwtProperties.SigningKey();
            signingKey.setKid("test-v1");
            try {
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair keyPair = keyGen.generateKeyPair();
                signingKey.setPublicKeyBase64(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
                signingKey.setPrivateKeyBase64(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate RSA key pair", e);
            }
            keys.add(signingKey);
            jwtProperties.setKeys(keys);
            return new JwtTokenService(authProperties(), jwtProperties, tokenBlacklistService(), clock());
        }

        @Bean
        public AuthenticationService authenticationService() {
            return new DefaultAuthenticationService(tokenService(), sessionService(), accountEpochService(),
                    authUserMapper(), authClientMapper(), authPlatformMapper(), clock());
        }

        @Bean
        public AuthService authService() {
            return new AuthServiceImpl(authUserMapper(), authClientMapper(), authPlatformMapper(),
                    authLoginAttemptMapper(), passwordEncoder(), loginAttemptService(), captchaService(),
                    tokenService(), tokenBlacklistService(), sessionService(), authProperties(), clock(),
                    accountEpochService(), authenticationService(), Mockito.mock(UserProfileSyncClient.class));
        }

        @Bean
        public SsoService ssoService(SecurityProperties securityProperties) {
            return new RedisSsoService(stringRedisTemplate(lettuceConnectionFactory()), authSessionObjectMapper(),
                    authRedisKeys(authProperties()), securityProperties, authenticationService(),
                    authService(), clock());
        }

        @Bean
        public AuthProperties authProperties() {
            AuthProperties props = new AuthProperties();
            props.setIssuer("yunanexus-test");
            props.setAccessTokenTtlSeconds(1800);
            props.setRefreshTokenTtlSeconds(604800);
            props.setSessionTtlSeconds(604800);
            props.setRedisKeyPrefix(TEST_PREFIX);
            return props;
        }


    }
}