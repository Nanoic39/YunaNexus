package cc.nanoic.yunanexus.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.auth.config.*;
import cc.nanoic.yunanexus.auth.domain.*;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.*;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.*;

class SsoIdentityAuthorizationTest {
    @Test
    void requiresWebIdentityBeforeCreatingRoot() {
        var redis = mock(StringRedisTemplate.class);
        var service =
                new RedisSsoService(
                        redis,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        new AuthRedisKeys(new AuthProperties()),
                        new SecurityProperties(),
                        mock(AuthenticationService.class),
                        mock(AuthService.class),
                        Clock.systemUTC());
        assertThrows(
                BizException.class,
                () ->
                        service.authorizeForIdentity(
                                new AuthorizeRequest(),
                                new AuthenticatedIdentity("external", "oauth_app", "sid", 7L)));
        verifyNoInteractions(redis);
    }

    @Test
    void bindsCodeToCurrentIdentityInsteadOfAnyBrowserCookie() {
        var redis = mock(StringRedisTemplate.class);
        var auth = mock(AuthenticationService.class);
        long now = 1700000000000L;
        var props = new SecurityProperties();
        props.setCodeTtlSeconds(60);
        var service =
                spy(
                        new RedisSsoService(
                                redis,
                                new com.fasterxml.jackson.databind.ObjectMapper(),
                                new AuthRedisKeys(new AuthProperties()),
                                props,
                                auth,
                                mock(AuthService.class),
                                Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)));
        var session = new SessionInfo();
        session.setUserId(7L);
        session.setExternalId("current");
        session.setSid("sid");
        session.setAccountEpoch("epoch");
        session.setExpireAtMillis(now + 3600000);
        when(auth.requireSession("sid")).thenReturn(session);
        var request = new AuthorizeRequest();
        request.setClientCode("web");
        request.setState("s".repeat(43));
        request.setRedirectUri("https://example.com/cb");
        request.setCodeChallenge("c".repeat(43));
        request.setCodeChallengeMethod("S256");
        when(redis.execute(
                        any(org.springframework.data.redis.core.script.RedisScript.class),
                        anyList(),
                        any(Object[].class)))
                .thenReturn(1L);
        doReturn("https://example.com/cb?code=code")
                .when(service)
                .authorize(eq(request), anyString());
        assertEquals(
                "https://example.com/cb?code=code",
                service.authorizeForIdentity(
                        request, new AuthenticatedIdentity("current", "web", "sid", 7L)));
        verify(auth).requireSession("sid");
        assertThrows(
                BizException.class,
                () ->
                        service.authorizeForIdentity(
                                request, new AuthenticatedIdentity("other", "web", "sid", 8L)));
    }
}
