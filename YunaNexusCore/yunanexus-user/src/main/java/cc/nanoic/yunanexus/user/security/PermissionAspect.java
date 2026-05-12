package cc.nanoic.yunanexus.user.security;

import cc.nanoic.yunanexus.common.security.annotation.RequirePermission;
import cc.nanoic.yunanexus.common.web.common.BusinessException;
import cc.nanoic.yunanexus.common.web.common.R;
import cc.nanoic.yunanexus.common.web.common.Result;
import cc.nanoic.yunanexus.user.client.AuthInternalClient;
import cc.nanoic.yunanexus.user.entity.DTO.PermissionEvaluateRequest;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
public class PermissionAspect {

    @Resource
    private AuthInternalClient authInternalClient;

    @Value("${yunanexus.security.authz.cache-ms:30000}")
    private long cacheMs;

    private final ConcurrentHashMap<String, Long> allowCache = new ConcurrentHashMap<>();

    /**
     * AOP切面处理带注解的权限接口
     * @param joinPoint 注入点
     * @param requirePermission 所需权限
     * @return 返回结果
     */
    @Around("@annotation(requirePermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        String authorization = getAuthorizationHeader();
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(R.NOT_LOGIN, "缺少有效Authorization");
        }

        String cacheKey = authorization + "|" + requirePermission.allMatch() + "|" + String.join(",", requirePermission.value());
        Long expireAt = allowCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (expireAt != null && expireAt > now) {
            return joinPoint.proceed();
        }

        Result<Map<String, Object>> resp;
        try {
            PermissionEvaluateRequest req = new PermissionEvaluateRequest();
            req.setPermissionCodes(requirePermission.value());
            req.setAllMatch(requirePermission.allMatch());
            resp = authInternalClient.evaluate(authorization, req);
        } catch (Exception e) {
            throw new BusinessException(R.SERVER_ERROR, "认证服务暂时不可用");
        }

        if (resp == null || resp.getCode() != R.SUCCESS.getCode() || resp.getData() == null) {
            throw new BusinessException(R.NOT_LOGIN, "token无效或已过期");
        }

        Object allowObj = resp.getData().get("allow");
        boolean allow = Boolean.TRUE.equals(allowObj) || "true".equalsIgnoreCase(String.valueOf(allowObj));
        if (!allow) {
            throw new BusinessException(R.NO_PERMISSION, "无权限访问该接口");
        }

        allowCache.put(cacheKey, now + cacheMs);
        return joinPoint.proceed();
    }

    private String getAuthorizationHeader() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader("Authorization");
    }

}
