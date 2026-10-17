package cc.nanoic.yunanexus.auth.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cc.nanoic.yunanexus.auth.domain.*;
import cc.nanoic.yunanexus.auth.security.AuthenticatedIdentity;
import cc.nanoic.yunanexus.auth.service.AdminService;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class AppealAuditIdentityTest {
    AdminService service; AuthAdminController controller;
    @BeforeEach
    void setup() {
        service = mock(AdminService.class); controller = new AuthAdminController();
        ReflectionTestUtils.setField(controller, "adminService", service);
    }
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }
    AppealAuditRequest request() {
        var body = new AppealAuditRequest(); body.setAuditor("forged-super-admin"); body.setRemark("checked"); return body;
    }
    void actor() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedIdentity("external", "web", "sid", 7L), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        var user = new UserView(); user.setUsername("actual-admin"); when(service.getUser(7L)).thenReturn(user);
    }
    @Test
    void approveCannotForgeAuditorFromBody() {
        actor(); controller.approveAppeal(8, request());
        verify(service).approveAppeal(8, "actual-admin", "checked");
    }
    @Test
    void rejectCannotForgeAuditorFromBody() {
        actor(); controller.rejectAppeal(8, request());
        verify(service).rejectAppeal(8, "actual-admin", "checked");
    }
    @Test
    void emergencyIdentityIsExplicit() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        controller.approveAppeal(8, request());
        verify(service).approveAppeal(8, "ops:emergency", "checked");
    }
    @Test
    void missingIdentityCannotWriteAudit() {
        assertEquals(403, assertThrows(BizException.class, () -> controller.approveAppeal(8, request())).getCode());
        verifyNoInteractions(service);
    }
}
