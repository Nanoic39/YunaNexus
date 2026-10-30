package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.auth.config.SecurityConfig;
import cc.nanoic.yunanexus.auth.controller.AuthController;
import cc.nanoic.yunanexus.auth.service.*;
import cc.nanoic.yunanexus.auth.service.impl.EmailCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real controller behind the full security chain, without external mail or state. */
class AuthPublicEndpointsMvcTest {
    @Test
    void anonymousEmailCodeRequiresCsrfAndDoesNotExposeOtherAuthEndpoints() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "yunanexus.auth.security.public-origin", "http://localhost:8100",
                    "yunanexus.auth.security.allow-local-http", true)));
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var mail = context.getBean(EmailCodeService.class);
            when(mail.send("test@example.com", "127.0.0.1")).thenReturn(true);

            mvc.perform(post("/api/v1/auth/email-code").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\"}"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(mail);

            var bootstrap = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
            var cookie = bootstrap.getResponse().getCookie("YunaCsrf_LOCAL");
            assertNotNull(cookie);
            String token = new ObjectMapper().readTree(bootstrap.getResponse().getContentAsString())
                    .get("data").get("token").asText();
            mvc.perform(post("/api/v1/auth/email-code").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\"}")
                    .cookie(cookie).header("X-XSRF-TOKEN", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
            verify(mail).send("test@example.com", "127.0.0.1");

            mvc.perform(post("/api/v1/auth/email-code").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\"}")
                    .cookie(cookie).header("X-XSRF-TOKEN", "wrong"))
                    .andExpect(status().isForbidden());
            verifyNoMoreInteractions(mail);
            for (String path : new String[]{"/api/v1/auth/email-code", "/api/v1/auth/me",
                    "/api/v1/auth/account/security", "/api/v1/auth/sso/unknown"}) {
                mvc.perform(get(path)).andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/api/v1/auth/password-reset/unknown")
                    .cookie(cookie).header("X-XSRF-TOKEN", token)).andExpect(status().isUnauthorized());
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthController.class})
    static class Config {
        @Bean(name = "authSessionObjectMapper") ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean AuthenticationService authenticationService() { return mock(AuthenticationService.class); }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean RegisterService registerService() { return mock(RegisterService.class); }
        @Bean UnlockAppealService unlockAppealService() { return mock(UnlockAppealService.class); }
        @Bean CaptchaService captchaService() { return mock(CaptchaService.class); }
        @Bean SsoService ssoService() { return mock(SsoService.class); }
        @Bean EmailCodeService emailCodeService() { return mock(EmailCodeService.class); }
    }
}
