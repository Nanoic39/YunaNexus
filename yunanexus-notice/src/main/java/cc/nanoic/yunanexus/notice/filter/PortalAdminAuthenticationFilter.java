package cc.nanoic.yunanexus.notice.filter;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.notice.config.NoticeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 门户管理接口独立校验管理员密钥，避免只有普通登录态即可修改门户。@author yunanexus */
@Component
public class PortalAdminAuthenticationFilter extends OncePerRequestFilter {
    private final NoticeProperties properties;
    private final ObjectMapper mapper;

    public PortalAdminAuthenticationFilter(NoticeProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals("/api/v1/notice/portal/admin")
                && !path.startsWith("/api/v1/notice/portal/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String expected = properties.getAdmin().getToken();
        String actual = request.getHeader("X-Admin-Token");
        if (expected == null || expected.isBlank() || actual == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(mapper.writeValueAsString(Result.error(401, "未认证")));
            return;
        }
        chain.doFilter(request, response);
    }
}
