package cc.nanoic.yunanexus.user.security;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 用户服务管理接口必须独立复验会话及管理员角色。 */
@Component
public class UserAdminFilter extends OncePerRequestFilter {
    private final AdminBearerVerifier verifier;
    private final ObjectMapper mapper;

    public UserAdminFilter(AdminBearerVerifier verifier, ObjectMapper mapper) {
        this.verifier = verifier;
        this.mapper = mapper;
    }

    protected boolean shouldNotFilter(HttpServletRequest r) {
        String p = r.getRequestURI();
        return !p.equals("/api/v1/user/admin") && !p.startsWith("/api/v1/user/admin/");
    }

    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
            throws ServletException, IOException {
        int status = verifier.verify(r.getHeader("Authorization"));
        if (status != 200) {
            s.setStatus(status);
            s.setContentType("application/json;charset=UTF-8");
            s.getWriter()
                    .write(
                            mapper.writeValueAsString(
                                    Result.error(status, status == 503 ? "管理权限校验不可用" : "无管理权限")));
            return;
        }
        c.doFilter(r, s);
    }
}
