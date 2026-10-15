package cc.nanoic.yunanexus.permission.security;

import cc.nanoic.yunanexus.permission.cache.AuthorizationCache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理后台写操作缓存失效过滤器。<p>
 *
 * 权限数据的所有变更都经由 {@code /api/v1/permission/admin/**}（非 GET）完成，
 * 因此在该路径的写请求进入前清空鉴权结果缓存，保证变更即时生效（与 TTL 双重兜底）。
 *
 * @author yunanexus
 */
@Component
public class AdminCacheEvictFilter extends OncePerRequestFilter {

    private final AuthorizationCache authorizationCache;

    public AdminCacheEvictFilter(AuthorizationCache authorizationCache) {
        this.authorizationCache = authorizationCache;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith(AdminTokenFilter.ADMIN_PATH_PREFIX)) {
            return true;
        }
        return HttpMethod.GET.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        authorizationCache.clear();
        filterChain.doFilter(request, response);
    }
}
