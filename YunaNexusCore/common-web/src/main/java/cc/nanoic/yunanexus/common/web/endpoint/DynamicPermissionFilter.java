package cc.nanoic.yunanexus.common.web.endpoint;

import cc.nanoic.yunanexus.common.web.auth.PermissionContext;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 动态接口权限过滤器。
 * 在 PermissionFilter 之后执行（已解析 JWT → PermissionContext），
 * 查询 EndpointReporter 本地缓存，校验当前用户是否有权限访问该接口。
 */
public class DynamicPermissionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DynamicPermissionFilter.class);

    private final EndpointReporter endpointReporter;

    public DynamicPermissionFilter(EndpointReporter endpointReporter) {
        this.endpointReporter = endpointReporter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!PermissionContext.hasIdentity()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        Set<String> permissions = PermissionContext.getPermissions();

        boolean allowed = endpointReporter.checkPermission(path, method, permissions);
        if (!allowed) {
            log.warn("动态权限拒绝: {} {} (permissions: {})", method, path, permissions);
            throw new BusinessException(R.NOT_PERMISSION, "缺少接口访问权限");
        }

        filterChain.doFilter(request, response);
    }
}
