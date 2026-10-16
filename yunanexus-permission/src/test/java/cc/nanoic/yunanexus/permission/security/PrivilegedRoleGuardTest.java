package cc.nanoic.yunanexus.permission.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;
import cc.nanoic.yunanexus.permission.entity.PermRole;
import cc.nanoic.yunanexus.permission.mapper.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;

import java.util.*;

class PrivilegedRoleGuardTest {
    @Test
    void normalAdminCannotAssignRootRolesAndSuperAdminCannotRemoveOwnRole() {
        var verifier = mock(AdminBearerVerifier.class);
        var roles = mock(PermRoleMapper.class);
        var bindings = mock(PermUserRoleMapper.class);
        var guard = new PrivilegedRoleGuard(verifier, roles, bindings);
        var r = new PermRole();
        r.setId(1L);
        r.setRoleCode("super_admin");
        when(roles.selectById(1L)).thenReturn(r);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            when(bindings.selectRoleIdsByUserId(8L)).thenReturn(List.of());
            when(verifier.actor("Bearer test"))
                    .thenReturn(new AdminBearerVerifier.Actor(7L, false));
            assertThrows(BizException.class, () -> guard.assignment(8L, List.of(1L)));
            when(verifier.actor("Bearer test")).thenReturn(new AdminBearerVerifier.Actor(7L, true));
            when(bindings.selectRoleIdsByUserId(7L)).thenReturn(List.of(1L));
            assertThrows(BizException.class, () -> guard.assignment(7L, List.of()));
            assertDoesNotThrow(() -> guard.assignment(8L, List.of(1L)));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
