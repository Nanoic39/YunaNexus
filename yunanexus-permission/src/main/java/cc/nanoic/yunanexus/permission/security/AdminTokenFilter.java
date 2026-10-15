package cc.nanoic.yunanexus.permission.security;

import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.permission.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 管理后台密钥鉴权过滤器。<p>
 *
 * 仅作用于 {@code /api/v1/permission/admin/**} 路径。客户端需在配置的请求头
 * （默认 {@code X-Admin-Token}）中携带与 {@link AdminProperties#getToken()} 一致的密钥。
 * 密钥未配置（空白）时管理后台视为未启用，一律拒绝（403）；密钥缺失或不匹配返回 401。
 * 校验通过后放行。<p>
 *
 * 密钥比较使用 {@link MessageDigest#isEqual} 恒定时间比较，避免时序侧信道。
 * 失败响应统一包装为 {@link Result}。
 *
 * @author yunanexus
 */
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    /** 管理后台路径前缀 */
    public static final String ADMIN_PATH_PREFIX = "/api/v1/permission/admin";

    private final cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier bearerVerifier;
    private final AdminProperties adminProperties;
    private final ObjectMapper objectMapper;

    public AdminTokenFilter(AdminProperties adminProperties, ObjectMapper objectMapper) {
        this(adminProperties, objectMapper, new cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier(""));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminTokenFilter(AdminProperties adminProperties, ObjectMapper objectMapper,
                            cc.nanoic.yunanexus.common.web.security.AdminBearerVerifier bearerVerifier) {
        this.bearerVerifier = bearerVerifier;
        this.adminProperties = adminProperties;
        this.objectMapper = objectMapper;
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
        String authorization=request.getHeader("Authorization");
        if (authorization != null) {
            int status=bearerVerifier.verify(authorization);
            if (status != 200) {
                response.setStatus(status);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(Result.error(status,
                    status==503 ? "管理权限校验不可用" : status==403 ? "无管理权限" : "未认证")));
                return;
            }
            filterChain.doFilter(request,response);
            return;
        }
        String configured = adminProperties.getToken();
        if (configured == null || configured.isBlank()) {
            writeError(response, 403, ErrorCode.ADMIN_DISABLED);
            return;
        }

        String presented = request.getHeader(adminProperties.getHeaderName());
        if (presented == null || presented.isBlank() || !matches(configured, presented)) {
            writeError(response, 401, ErrorCode.ADMIN_TOKEN_INVALID);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String configured, String presented) {
        return MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, int httpStatus, ErrorCode errorCode) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(errorCode.getCode(), errorCode.getMsg())));
    }
}
