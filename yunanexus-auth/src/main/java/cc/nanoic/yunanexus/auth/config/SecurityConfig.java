package cc.nanoic.yunanexus.auth.config;

import cc.nanoic.yunanexus.auth.security.AdminAuthenticationFilter;
import cc.nanoic.yunanexus.auth.security.JwtAuthenticationFilter;
import cc.nanoic.yunanexus.auth.security.SecurityErrorWriter;
import cc.nanoic.yunanexus.auth.service.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({SecurityProperties.class, AdminProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties securityProperties,
            AdminProperties adminProperties,
            AuthenticationManager authenticationManager,
            org.springframework.beans.factory.ObjectProvider<cc.nanoic.yunanexus.auth.client.RootAdminClient> roleClients,
            org.springframework.beans.factory.ObjectProvider<cc.nanoic.yunanexus.auth.service.OAuthApplicationService> applicationServices,
            @Qualifier("authSecurityErrorWriter") SecurityErrorWriter securityErrorWriter) throws Exception {

        // CSRF 配置
        CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
        csrfTokenRepository.setCookieName(securityProperties.getCsrfCookieName());
        csrfTokenRepository.setCookiePath("/");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");
        csrfTokenRepository.setCookieCustomizer(cookie -> {
            cookie.secure(securityProperties.isSecureCookie());
            cookie.sameSite("Lax");
            cookie.httpOnly(true);
        });

        XorCsrfTokenRequestAttributeHandler csrfHandler = new XorCsrfTokenRequestAttributeHandler();

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(authenticationManager, securityErrorWriter);
        jwtFilter.setClientAuthorization((i,r)->{
            if("/internal/auth/oauth/allow".equals(r.getRequestURI()))return true;
            var apps=applicationServices.getIfAvailable();
            return apps!=null&&apps.allows(i.clientCode(),r.getMethod(),r.getRequestURI());
        });
        AdminAuthenticationFilter adminFilter = new AdminAuthenticationFilter(adminProperties, securityErrorWriter, userId -> {
            var client=roleClients.getIfAvailable();
            if (client==null) throw new IllegalStateException("Role verification unavailable");
            var result=client.check(userId);
            if (result==null || result.getCode()!=200) throw new IllegalStateException("Role verification unavailable");
            return Boolean.TRUE.equals(result.getData());
        });

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers("/internal/auth/api-key/verify", "/api/v1/auth/sso/token", AdminAuthenticationFilter.ADMIN_PATH_PREFIX + "/**")
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .requestCache(cache -> cache.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource(securityProperties, adminProperties)))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> {
                    if (securityProperties.isSecureCookie()) {
                        hsts.maxAgeInSeconds(31536000).includeSubDomains(false).preload(false);
                    } else {
                        hsts.disable();
                    }
                })
                .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .cacheControl(org.springframework.security.config.Customizer.withDefaults())
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    securityErrorWriter.write(response, 401, "未认证"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    securityErrorWriter.write(response, 403, "无权限"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/internal/auth/admin/check", "/internal/auth/admin/super-check", "/internal/auth/admin/identity", "/internal/auth/oauth/allow").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/captcha").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
                    "/api/v1/auth/refresh", "/api/v1/auth/unlock-appeal",
                    "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/verify", "/api/v1/auth/password-reset/complete",
                    "/api/v1/auth/sso/token", "/api/v1/auth/sso/authorize").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout", "/api/v1/auth/logout-all").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                .requestMatchers("/api/v1/auth/account/**").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/auth/application/permissions/check").authenticated()
                .requestMatchers(HttpMethod.POST,"/internal/auth/api-key/verify").permitAll()
                .requestMatchers(AdminAuthenticationFilter.ADMIN_PATH_PREFIX + "/**").hasRole("ADMIN")
                .anyRequest().denyAll()
            )
            .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(adminFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationService authenticationService) {
        AuthenticationProvider provider = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                if (authentication instanceof PreAuthenticatedAuthenticationToken preAuthToken) {
                    Object principal = preAuthToken.getPrincipal();
                    if (principal instanceof String accessToken) {
                        var identity = authenticationService.authenticateAccess(accessToken);
                        return new UsernamePasswordAuthenticationToken(
                            identity,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                    }
                }
                throw new AuthenticationException("Invalid pre-authenticated token") {};
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
        return new ProviderManager(provider);
    }

    @Bean
    @Qualifier("authSecurityErrorWriter")
    public SecurityErrorWriter securityErrorWriter(@Qualifier("authSessionObjectMapper") ObjectMapper objectMapper) {
        return new SecurityErrorWriter(objectMapper);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties securityProperties,
                                                           AdminProperties adminProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(securityProperties.getCorsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN",
            adminProperties.getHeaderName()));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
