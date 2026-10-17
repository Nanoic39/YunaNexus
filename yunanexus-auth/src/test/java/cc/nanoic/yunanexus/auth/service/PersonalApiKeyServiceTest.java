package cc.nanoic.yunanexus.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.auth.entity.*;
import cc.nanoic.yunanexus.auth.mapper.*;
import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.*;

class PersonalApiKeyServiceTest {
    PersonalApiKeyMapper mapper = mock(PersonalApiKeyMapper.class);
    AuthUserMapper users = mock(AuthUserMapper.class);
    AuthPlatformMapper grants = mock(AuthPlatformMapper.class);
    AuthClientMapper clients = mock(AuthClientMapper.class);
    PersonalApiKeyService service = new PersonalApiKeyService(mapper, users, grants, clients);

    @Test
    void storesOnlyHashAndReturnsSecretOnce() {
        when(mapper.lockOwner(9)).thenReturn(9L);
        when(mapper.list(9)).thenReturn(List.of());
        var created = service.create(9, "backup", List.of("files:read"), 7);
        assertTrue(created.token().matches("ynk_[A-Za-z0-9_-]{43}"));
        var captor = org.mockito.ArgumentCaptor.forClass(PersonalApiKey.class);
        verify(mapper).insert(captor.capture());
        assertNotEquals(created.token(), captor.getValue().getTokenHash());
        assertEquals(PersonalApiKeyService.hash(created.token()), captor.getValue().getTokenHash());
        assertEquals(64, captor.getValue().getTokenHash().length());
        assertFalse(created.key().toString().contains(created.token()));
    }

    @Test
    void forbidsInvalidScopesAndExpiry() {
        assertThrows(BizException.class, () -> service.create(9, "test", List.of("admin:*"), 30));
        assertThrows(
                BizException.class, () -> service.create(9, "test", List.of("profile:read"), 91));
        assertThrows(BizException.class, () -> service.create(9, "test", List.of(), 30));
        verifyNoInteractions(mapper);
    }

    @Test
    void scopesUseExactMethodsAndPaths() {
        var scopes = Set.of("profile:read", "files:read", "files:write");
        assertTrue(PersonalApiKeyService.allows(scopes, "GET", "/api/v1/user/profile"));
        assertTrue(PersonalApiKeyService.allows(scopes, "GET", "/api/v1/file/me/123/download"));
        assertTrue(PersonalApiKeyService.allows(scopes, "POST", "/api/v1/file/me/upload"));
        for (var path :
                List.of(
                        "/api/v1/auth/account/api-keys",
                        "/api/v1/file/me/shares",
                        "/api/v1/auth/admin/users",
                        "/api/v1/file/download/123",
                        "/api/v1/file/me/../admin"))
            assertFalse(PersonalApiKeyService.allows(scopes, "GET", path), path);
        assertFalse(PersonalApiKeyService.allows(scopes, "PUT", "/api/v1/user/profile"));
        assertFalse(
                PersonalApiKeyService.allows(
                        Set.of("files:read"), "POST", "/api/v1/file/me/123/delete"));
    }

    @Test
    void renameRequiresWriteScopeAndExactRoute() {
        assertTrue(
                PersonalApiKeyService.allows(
                        Set.of("files:write"), "POST", "/api/v1/file/me/123/rename"));
        assertFalse(
                PersonalApiKeyService.allows(
                        Set.of("files:read"), "POST", "/api/v1/file/me/123/rename"));
        assertFalse(
                PersonalApiKeyService.allows(
                        Set.of("files:write"), "GET", "/api/v1/file/me/123/rename"));
        assertFalse(
                PersonalApiKeyService.allows(
                        Set.of("files:write"), "POST", "/api/v1/file/me/0/rename"));
    }

    @Test
    void rejectsRevokedMissingAndExpiredKeys() {
        String token = "ynk_" + "a".repeat(43);
        assertThrows(BizException.class, () -> service.verify(token, "GET", "/api/v1/file/me"));
        var key = key();
        key.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1));
        when(mapper.find(anyString())).thenReturn(key);
        assertThrows(BizException.class, () -> service.verify(token, "GET", "/api/v1/file/me"));
        key.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(1));
        key.setRevoked(1);
        assertThrows(BizException.class, () -> service.verify(token, "GET", "/api/v1/file/me"));
        verifyNoInteractions(users);
    }

    @Test
    void rechecksUserAndGrantAndReturnsServerIdentity() {
        String token = "ynk_" + "a".repeat(43);
        when(mapper.find(anyString())).thenReturn(key());
        AuthUser user = new AuthUser();
        user.setUserId(9L);
        user.setShardIndex(2);
        user.setStatus(1);
        when(users.selectByUserId(9)).thenReturn(user);
        AuthPlatform grant = new AuthPlatform();
        grant.setStatus(1);
        when(grants.selectByUserIdAndClient(9, "web")).thenReturn(grant);
        AuthClient client = new AuthClient();
        client.setApplicationCategory("OFFICIAL_NATIVE");
        client.setStatus(1);
        when(clients.selectByClientCode("web")).thenReturn(client);
        assertEquals(
                new PersonalApiKeyService.Verified(9, 2),
                service.verify(token, "GET", "/api/v1/file/me"));
        user.setStatus(2);
        assertThrows(BizException.class, () -> service.verify(token, "GET", "/api/v1/file/me"));
        user.setStatus(1);
        grant.setStatus(0);
        assertThrows(BizException.class, () -> service.verify(token, "GET", "/api/v1/file/me"));
    }

    @Test
    void revokeAlwaysIncludesOwner() {
        when(mapper.revoke("id", 9)).thenReturn(0);
        assertThrows(BizException.class, () -> service.revoke(9, "id"));
        verify(mapper).revoke("id", 9);
    }

    private PersonalApiKey key() {
        var k = new PersonalApiKey();
        k.setId("key");
        k.setUserId(9L);
        k.setRevoked(0);
        k.setScopes("files:read");
        k.setExpiresAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(1));
        return k;
    }
    @Test
    void creationAndExpiryUseUtcEvenOnNonUtcHost() {
        var original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
            when(mapper.lockOwner(9)).thenReturn(9L);
            when(mapper.list(9)).thenReturn(List.of());
            var before = java.time.Instant.now();
            var result = service.create(9, "clock-test", List.of("files:read"), 7).key();
            var instant = result.createdAt().toInstant(java.time.ZoneOffset.UTC);
            assertTrue(Math.abs(java.time.Duration.between(before, instant).toSeconds()) < 5);
            assertEquals(result.createdAt().plusDays(7), result.expiresAt());
        } finally { java.util.TimeZone.setDefault(original); }
    }
}
