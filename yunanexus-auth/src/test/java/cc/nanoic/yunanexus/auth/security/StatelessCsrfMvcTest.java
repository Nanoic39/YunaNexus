package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.auth.config.SecurityConfig;
import cc.nanoic.yunanexus.auth.service.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Simulates a browser retaining one CSRF token across authenticated page reads and writes. */
class StatelessCsrfMvcTest {
    @Test
    void authenticatedRequestsPreserveCsrfCookieAndMissingOrWrongTokensStillFail() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "yunanexus.auth.security.public-origin", "http://localhost:8100",
                "yunanexus.auth.security.allow-local-http", true)));
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            var bootstrap = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
            var cookie = bootstrap.getResponse().getCookie("YunaCsrf_LOCAL");
            assertNotNull(cookie);
            var token = new ObjectMapper().readTree(bootstrap.getResponse().getContentAsString()).get("token").asText();
            for (int i = 0; i < 3; i++) {
                var read = mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer fixture").cookie(cookie))
                    .andExpect(status().isOk()).andReturn();
                assertNull(read.getResponse().getCookie(cookie.getName()), "Bearer authentication must not delete the browser CSRF cookie");
                var write = mvc.perform(post("/api/v1/auth/account/csrf-probe").header("Authorization", "Bearer fixture")
                    .cookie(cookie).header("X-XSRF-TOKEN", token)).andExpect(status().isOk()).andReturn();
                assertNull(write.getResponse().getCookie(cookie.getName()));
            }
            mvc.perform(post("/api/v1/auth/account/csrf-probe").header("Authorization", "Bearer fixture").cookie(cookie))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/auth/account/csrf-probe").header("Authorization", "Bearer fixture")
                .cookie(cookie).header("X-XSRF-TOKEN", "wrong")).andExpect(status().isForbidden());
        }
    }
    @Configuration @EnableWebMvc @Import({SecurityConfig.class, Probe.class})
    static class Config {
        @Bean(name="authSessionObjectMapper") ObjectMapper mapper() { return new ObjectMapper(); }
        @Bean AuthenticationService authenticationService() {
            var service = mock(AuthenticationService.class);
            when(service.authenticateAccess("fixture")).thenReturn(new AuthenticatedIdentity("fixture", "web", "session", 123));
            return service;
        }
    }
    @RestController static class Probe {
        @GetMapping("/api/v1/auth/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken()); }
        @GetMapping("/api/v1/auth/me") String me() { return "ok"; }
        @PostMapping("/api/v1/auth/account/csrf-probe") String write() { return "ok"; }
    }
}
