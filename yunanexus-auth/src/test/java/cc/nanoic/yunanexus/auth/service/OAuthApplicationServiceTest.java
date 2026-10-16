package cc.nanoic.yunanexus.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

class OAuthApplicationServiceTest {
    private OAuthApplicationService.Save body(List<String> urls, List<String> scopes) {
        return new OAuthApplicationService.Save(
                "test", null, null, "https://example.com", urls, scopes);
    }

    @Test
    void rejectsUnsafeCallbacksAndUnknownScopes() {
        for (String uri :
                List.of(
                        "javascript:alert(1)",
                        "https://user:pass@example.com/cb",
                        "https://example.com/cb#part",
                        "https://example.com/cb?%63ode=x",
                        "http://example.com/cb",
                        "https://example.com/cb\n"))
            assertThrows(
                    BizException.class,
                    () ->
                            OAuthApplicationService.validate(
                                    body(List.of(uri), List.of("profile:read"))));
        assertThrows(
                BizException.class,
                () ->
                        OAuthApplicationService.validate(
                                body(List.of("https://example.com/cb"), List.of("admin:all"))));
        assertDoesNotThrow(
                () ->
                        OAuthApplicationService.validate(
                                body(
                                        List.of(
                                                "https://example.com/cb",
                                                "http://127.0.0.1:5000/cb"),
                                        List.of("profile:read"))));
    }

    @Test
    void userListingIsOwnerBoundAndDoesNotExposeCredential() {
        var db = mock(JdbcTemplate.class);
        var service = new OAuthApplicationService(db);
        var row = new LinkedHashMap<String, Object>();
        row.put("redirectUris", "https://example.com/cb");
        row.put("scopes", "profile:read");
        row.put("id", 1L);
        when(db.queryForList(contains("AND a.owner_user_id=?"), eq(7L))).thenReturn(List.of(row));
        var list = service.list(7L);
        assertEquals(1, list.size());
        assertNull(list.getFirst().get("clientSecret"));
        assertFalse(list.getFirst().containsKey("secret_hash"));
        assertEquals(List.of("profile:read"), list.getFirst().get("scopes"));
        verify(db).queryForList(contains("AND a.owner_user_id=?"), eq(7L));
    }

    @Test
    void scopesDenyAdministrationAndGateEmailExposure() {
        var db = mock(JdbcTemplate.class);
        var service = new OAuthApplicationService(db);
        when(db.queryForList(startsWith("SELECT scopes"), eq("oauth_test")))
                .thenReturn(List.of(Map.of("scopes", "profile:read,files:read")));
        assertTrue(service.allows("oauth_test", "GET", "/api/v1/auth/me"));
        assertTrue(service.allows("oauth_test", "GET", "/api/v1/file/me"));
        assertFalse(service.allows("oauth_test", "GET", "/api/v1/user/profile"));
        assertFalse(service.allows("oauth_test", "GET", "/api/v1/auth/admin/users"));
        assertFalse(service.allows("oauth_test", "POST", "/api/v1/auth/account/api-keys"));
        assertFalse(service.allows("oauth_test", "POST", "/api/v1/file/me/upload"));
    }

    @Test
    void deletedOrDisabledClientsCannotExchangeSecrets() {
        var service = new OAuthApplicationService(mock(JdbcTemplate.class));
        assertThrows(BizException.class, () -> service.verifySecret("oauth_unknown", "wrong"));
    }
    @Test void missingOwnedApplicationUses404InsteadOf500(){var handler=new cc.nanoic.yunanexus.auth.security.AuthSecurityExceptionHandler();var result=handler.handleBizException(new BizException(cc.nanoic.yunanexus.common.web.constant.ErrorCode.NOT_FOUND,"应用不存在"));assertEquals(404,result.getStatusCode().value());}
}
