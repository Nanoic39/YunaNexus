package cc.nanoic.yunanexus.auth.security;

import cc.nanoic.yunanexus.auth.config.AdminProperties;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 管理后台密钥鉴权过滤器。<p>
 *
 * 仅作用于 {@code /api/v1/auth/admin/**} 路径。客户端需在配置的请求头
 * （默认 {@code X-Admin-Token}）中携带与 {@link AdminProperties#getToken()} 一致的
 * 密钥。密钥未配置（空白）时管理后台视为未启用，一律拒绝访问；密钥缺失或不匹配
 * 返回 401。校验通过后写入 {@code ROLE_ADMIN} 身份，交由后续授权规则放行。<p>
 *
 * 密钥比较使用 {@link MessageDigest#isEqual} 恒定时间比较，避免时序侧信道。
 *
 * @author yunanexus
 */
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    /** 管理后台路径前缀 */
    public static final String ADMIN_PATH_PREFIX = "/api/v1/auth/admin";

    private final AdminProperties adminProperties;
    private final SecurityErrorWriter securityErrorWriter;

    public AdminAuthenticationFilter(AdminProperties adminProperties, SecurityErrorWriter securityErrorWriter) {
        this.adminProperties = adminProperties;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String configured = adminProperties.getToken();
        if (configured == null || configured.isBlank()) {
            securityErrorWriter.write(response, 403, ErrorCode.ADMIN_DISABLED.getCode(), "管理后台未启用");
            return;
        }

        String presented = request.getHeader(adminProperties.getHeaderName());
        if (presented == null || presented.isBlank() || !matches(configured, presented)) {
            securityErrorWriter.write(response, 401, ErrorCode.ADMIN_TOKEN_INVALID.getCode(), "管理员密钥无效");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private boolean matches(String configured, String presented) {
        return MessageDigest.isEqual(
            configured.getBytes(StandardCharsets.UTF_8),
            presented.getBytes(StandardCharsets.UTF_8));
    }
}
