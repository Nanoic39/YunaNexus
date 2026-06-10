package cc.nanoic.yunanexus.common.web.auth;

import cn.hutool.core.text.AntPathMatcher;
import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.redisson.api.RedissonClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

public class PermissionFilter extends OncePerRequestFilter {
    private final byte[] jwtSecret;
    private final RedissonClient redisson;
    private final List<String> excludePaths;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public PermissionFilter(byte[] jwtSecret, RedissonClient redisson, List<String> excludePaths) {
        this.jwtSecret = jwtSecret;
        this.redisson = redisson;
        this.excludePaths = excludePaths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isExcluded(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String userUuid = request.getHeader("X-User-Uuid");
            if (userUuid != null && !userUuid.isEmpty()) {
                loadFromHeaders(request);
            } else {
                loadFromJwt(request);
            }
        } catch (Exception e) {
            PermissionContext.clear();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            PermissionContext.clear();
        }
    }

    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : excludePaths) {
            if (matcher.match(pattern, path)) return true;
        }
        return false;
    }

    private void loadFromHeaders(HttpServletRequest request) {
        PermissionContext.setUuid(request.getHeader("X-User-Uuid"));
        String gidHex = request.getHeader("X-User-Global-Id");
        if (gidHex != null) {
            PermissionContext.setGlobalId(HexUtil.decodeHex(gidHex));
        }
        PermissionContext.setRoles(parseJsonSet(request.getHeader("X-User-Roles")));
        PermissionContext.setPermissions(parseJsonSet(request.getHeader("X-User-Permissions")));
    }

    @SuppressWarnings("unchecked")
    private Set<String> parseJsonSet(String json) {
        if (json == null || json.isEmpty()) return Collections.emptySet();
        return new HashSet<>(JSON.parseArray(json, String.class));
    }

    private void loadFromJwt(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return;
        }
        String token = auth.substring(7);

        JwtUtil.JwtPayload payload = JwtUtil.parseToken(token, jwtSecret);
        if (payload == null) return;

        Set<String> effectiveRoles = applyRedisOverlay(payload.uuid, payload.roles, "jwt:revoked:");
        effectiveRoles = applyRedisGrant(payload.uuid, effectiveRoles, "jwt:granted:");

        Set<String> effectivePermissions = new HashSet<>(payload.permissions);

        PermissionContext.setUuid(payload.uuid);
        PermissionContext.setGlobalId(payload.globalId);
        PermissionContext.setRoles(effectiveRoles);
        PermissionContext.setPermissions(effectivePermissions);
    }

    private Set<String> applyRedisOverlay(String uuid, Set<String> original, String prefix) {
        Map<String, String> revoked = redisson.<String, String>getMap(prefix + uuid).readAllMap();
        if (revoked.isEmpty()) return original;
        Set<String> result = new HashSet<>(original);
        result.removeAll(revoked.keySet());
        return result;
    }

    private Set<String> applyRedisGrant(String uuid, Set<String> original, String prefix) {
        Map<String, String> granted = redisson.<String, String>getMap(prefix + uuid).readAllMap();
        if (granted.isEmpty()) return original;
        Set<String> result = new HashSet<>(original);
        result.addAll(granted.keySet());
        return result;
    }
}
