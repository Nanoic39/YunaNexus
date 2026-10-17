package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.auth.config.AuthProperties;
import cc.nanoic.yunanexus.auth.config.AuthRedisKeys;
import cc.nanoic.yunanexus.auth.config.JwtProperties;
import cc.nanoic.yunanexus.auth.config.SecurityConfig;
import cc.nanoic.yunanexus.auth.config.SecurityProperties;
import cc.nanoic.yunanexus.auth.controller.AuthController;
import cc.nanoic.yunanexus.auth.controller.SsoController;
import cc.nanoic.yunanexus.auth.domain.LoginRequest;
import cc.nanoic.yunanexus.auth.domain.RegisterRequest;
import cc.nanoic.yunanexus.auth.domain.UnlockAppealRequest;
import cc.nanoic.yunanexus.auth.entity.AuthClient;
import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import cc.nanoic.yunanexus.auth.entity.AuthUser;
import cc.nanoic.yunanexus.auth.mapper.AuthClientMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthLoginAttemptMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthPlatformMapper;
import cc.nanoic.yunanexus.auth.mapper.AuthUserMapper;
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
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证中心 HTTP 状态码映射集成测试（MockMvc，无 Redis/DB）。<p>
 * <p>
 * 验证 {@link AuthSecurityExceptionHandler} 按 docs/auth-security-plan.md §9 的映射约定
 * 输出正确的 HTTP 状态码：凭证失败 401（<b>不得为 403</b>）、授权失败 403、
 * 参数/前置条件 400、注册/申诉冲突 409、基础设施故障 503。
 * <p>
 * 无需 Redis：会话/纪元/撤销服务全部以 Mockito 桩替代。
 *
 * @author yunanexus
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthHttpStatusMappingTest {

    private static final String CLIENT_WEB = "web";
    private static final String TEST_USERNAME = "alice";
    private static final String TEST_PASSWORD = "test-only-password";

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthUserMapper authUserMapper;
    private AuthClientMapper authClientMapper;
    private AuthPlatformMapper authPlatformMapper;
    private LoginAttemptService loginAttemptService;
    private CaptchaService captchaService;
    private RegisterService registerService;
    private UnlockAppealService unlockAppealService;

    private AuthUser aliceUser;

    @BeforeAll
    void setUpAll() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("local");
        Map<String, Object> props = new HashMap<>();
        props.put("yunanexus.auth.security.public-origin", "http://localhost:8100");
        props.put("yunanexus.auth.security.allow-local-http", true);
        props.put("yunanexus.auth.jwt.active-kid", "test-v1");
        env.getPropertySources().addFirst(new MapPropertySource("testProps", props));
        context.setEnvironment(env);
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();

        objectMapper = context.getBean("authSessionObjectMapper", ObjectMapper.class);
        authUserMapper = context.getBean(AuthUserMapper.class);
        authClientMapper = context.getBean(AuthClientMapper.class);
        authPlatformMapper = context.getBean(AuthPlatformMapper.class);
        loginAttemptService = context.getBean(LoginAttemptService.class);
        captchaService = context.getBean(CaptchaService.class);
        registerService = context.getBean(RegisterService.class);
        unlockAppealService = context.getBean(UnlockAppealService.class);

        mockMvc = MockMvcBuilders
                .webAppContextSetup((WebApplicationContext) context)
                .apply(springSecurity())
                .build();
    }

    /**
     * 每次请求前重置桩：默认客户端与用户存在且启用，平台授权正常。
     */
    private void resetStubs() {
        Mockito.reset(authUserMapper, authClientMapper, authPlatformMapper,
                loginAttemptService, captchaService, registerService, unlockAppealService);

        aliceUser = new AuthUser();
        aliceUser.setUserId(10001L);
        aliceUser.setExternalId("ext-alice");
        aliceUser.setUsername(TEST_USERNAME);
        aliceUser.setPassword(new BCryptPasswordEncoder().encode(TEST_PASSWORD));
        aliceUser.setStatus(1);
        aliceUser.setFailCount(0);

        AuthClient client = new AuthClient();
        client.setApplicationCategory("OFFICIAL_NATIVE");
        client.setClientCode(CLIENT_WEB);
        client.setStatus(1);

        AuthPlatform platform = new AuthPlatform();
        platform.setUserId(10001L);
        platform.setClientCode(CLIENT_WEB);
        platform.setStatus(1);

        when(authUserMapper.selectByUsername(TEST_USERNAME)).thenReturn(aliceUser);
        when(authUserMapper.selectByEmail(anyString())).thenReturn(null);
        when(authUserMapper.selectByMobile(anyString(), anyString())).thenReturn(null);
        when(authClientMapper.selectByClientCode(CLIENT_WEB)).thenReturn(client);
        when(authPlatformMapper.selectByUserIdAndClient(10001L, CLIENT_WEB)).thenReturn(platform);
        when(loginAttemptService.captchaRequired(anyString())).thenReturn(false);
        when(loginAttemptService.isLocked(anyString())).thenReturn(false);
    }

    /** 引导 CSRF（同源，无 Cookie 依赖）。 */
    private Csrf bootstrapCsrf() throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        var json = objectMapper.readTree(body);
        String rawToken = json.get("data").get("token").asText();
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        String cookieValue = null;
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("YunaCsrf_LOCAL=")) {
                cookieValue = trimmed.substring("YunaCsrf_LOCAL=".length());
            }
        }
        return new Csrf(rawToken, cookieValue);
    }

    private record Csrf(String rawToken, String cookieValue) {
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path, Csrf csrf,
                                                                                           Object body) throws Exception {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header("X-XSRF-TOKEN", csrf.rawToken())
                .cookie(new Cookie("YunaCsrf_LOCAL", csrf.cookieValue()));
    }

    @Test
    void 凭证错误应返回401而非403() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword("wrong-password");
        req.setClientCode(CLIENT_WEB);

        mockMvc.perform(post("/api/v1/auth/login", csrf, req))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS.getCode()));
    }

    @Test
    void 账号不存在应返回401而非500() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        when(authUserMapper.selectByUsername(TEST_USERNAME)).thenReturn(null);
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setClientCode(CLIENT_WEB);

        mockMvc.perform(post("/api/v1/auth/login", csrf, req))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 需要验证码应返回400() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        when(loginAttemptService.captchaRequired(TEST_USERNAME)).thenReturn(true);
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setClientCode(CLIENT_WEB);

        mockMvc.perform(post("/api/v1/auth/login", csrf, req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAPTCHA_REQUIRED.getCode()));
    }

    @Test
    void 验证码错误应返回400() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        when(loginAttemptService.captchaRequired(TEST_USERNAME)).thenReturn(true);
        when(captchaService.verify("cap-1", "000000")).thenReturn(false);
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setClientCode(CLIENT_WEB);
        req.setCaptchaId("cap-1");
        req.setCaptcha("000000");

        mockMvc.perform(post("/api/v1/auth/login", csrf, req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAPTCHA_ERROR.getCode()));
    }

    @Test
    void 账号禁用应返回403() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        aliceUser.setStatus(0);
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setClientCode(CLIENT_WEB);

        mockMvc.perform(post("/api/v1/auth/login", csrf, req))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_DISABLED.getCode()));
    }

    @Test
    void 客户端不存在应返回403() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        LoginRequest req = new LoginRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);
        req.setClientCode("unknown-client");

        mockMvc.perform(post("/api/v1/auth/login", csrf, req))
                .andExpect(status().isForbidden());
    }

    @Test
    void 注册重复账号应返回409() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        when(registerService.register(any(RegisterRequest.class)))
                .thenThrow(new BizException(ErrorCode.REGISTER_USER_ALREADY_EXISTS));
        RegisterRequest req = new RegisterRequest();
        req.setUsername("bobby");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register", csrf, req))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.REGISTER_USER_ALREADY_EXISTS.getCode()));
    }

    @Test
    void 注册处理中应返回409() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        when(registerService.register(any(RegisterRequest.class)))
                .thenThrow(new BizException(ErrorCode.REGISTER_PROCESSING));
        RegisterRequest req = new RegisterRequest();
        req.setUsername("bobby");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register", csrf, req))
                .andExpect(status().isConflict());
    }

    @Test
    void 申诉重复应返回409() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        when(unlockAppealService.submit(any(UnlockAppealRequest.class)))
                .thenThrow(new BizException(ErrorCode.APPEAL_ALREADY_PENDING));
        UnlockAppealRequest req = new UnlockAppealRequest();
        req.setUsername(TEST_USERNAME);
        req.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/v1/auth/unlock-appeal", csrf, req))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.APPEAL_ALREADY_PENDING.getCode()));
    }

    @Test
    void 注册服务不可用应返回503且不泄露细节() throws Exception {
        resetStubs();
        Csrf csrf = bootstrapCsrf();
        when(registerService.register(any(RegisterRequest.class)))
                .thenThrow(new BizException(ErrorCode.REGISTER_FAILED, "取号服务不可用"));
        RegisterRequest req = new RegisterRequest();
        req.setUsername("bobby");
        req.setPassword("password123");

        mockMvc.perform(post("/api/v1/auth/register", csrf, req))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.REGISTER_FAILED.getCode()))
                .andExpect(jsonPath("$.msg").value("服务不可用"));
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthController.class, SsoController.class, AuthSecurityExceptionHandler.class})
    static class TestConfig {

        @Bean
        public Clock clock() {
            return Clock.systemUTC();
        }

        @Bean(name = "authSessionObjectMapper")
        public ObjectMapper authSessionObjectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public AuthRedisKeys authRedisKeys(AuthProperties authProperties) {
            return new AuthRedisKeys(authProperties);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthProperties authProperties() {
            AuthProperties props = new AuthProperties();
            props.setIssuer("yunanexus-test");
            props.setAccessTokenTtlSeconds(1800);
            props.setRefreshTokenTtlSeconds(604800);
            props.setSessionTtlSeconds(604800);
            return props;
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
            return Mockito.mock(TokenBlacklistService.class);
        }

        @Bean
        public SessionService sessionService() {
            return Mockito.mock(SessionService.class);
        }

        @Bean
        public AccountEpochService accountEpochService() {
            return Mockito.mock(AccountEpochService.class);
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
                    accountEpochService(), authenticationService(), Mockito.mock(cc.nanoic.yunanexus.auth.client.UserProfileSyncClient.class), Mockito.mock(cc.nanoic.yunanexus.auth.service.OAuthApplicationService.class));
        }

        @Bean
        public SsoService ssoService(SecurityProperties securityProperties) {
            return Mockito.mock(SsoService.class);
        }
    }
}
