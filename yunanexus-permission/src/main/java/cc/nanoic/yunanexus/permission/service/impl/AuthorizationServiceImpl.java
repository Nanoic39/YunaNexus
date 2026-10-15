package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.permission.abac.AbacConditionEvaluator;
import cc.nanoic.yunanexus.permission.cache.AuthorizationCache;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.entity.PermPolicy;
import cc.nanoic.yunanexus.permission.entity.PermResourceGrant;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermPolicyMapper;
import cc.nanoic.yunanexus.permission.mapper.PermResourceGrantMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;

/**
 * 鉴权判定服务实现。
 *
 * @author yunanexus
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private final PermPolicyMapper policyMapper;
    private final PermPermissionMapper permissionMapper;
    private final PermResourceGrantMapper grantMapper;
    private final PermUserRoleMapper userRoleMapper;
    private final PermUserDeptMapper userDeptMapper;
    private final AbacConditionEvaluator conditionEvaluator;
    private final AuthorizationCache authorizationCache;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthorizationServiceImpl(PermPolicyMapper policyMapper,
                                    PermPermissionMapper permissionMapper,
                                    PermResourceGrantMapper grantMapper,
                                    PermUserRoleMapper userRoleMapper,
                                    PermUserDeptMapper userDeptMapper,
                                    AbacConditionEvaluator conditionEvaluator,
                                    AuthorizationCache authorizationCache) {
        this.policyMapper = policyMapper;
        this.permissionMapper = permissionMapper;
        this.grantMapper = grantMapper;
        this.userRoleMapper = userRoleMapper;
        this.userDeptMapper = userDeptMapper;
        this.conditionEvaluator = conditionEvaluator;
        this.authorizationCache = authorizationCache;
    }

    @Override
    public boolean check(long userId, String resource, String action, Map<String, Object> context) {
        return check(userId, resource, action, null, null, context);
    }

    @Override
    public boolean check(long userId, String resource, String action, String resourceType, String resourceId,
                         Map<String, Object> context) {
        // 仅缓存「无 ABAC 上下文」的判定：上下文参与判定时结果随属性变化，不做缓存
        boolean cacheable = context == null || context.isEmpty();
        String key = null;
        if (cacheable) {
            key = cacheKey(userId, resource, action, resourceType, resourceId);
            Boolean cached = authorizationCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        boolean result = doCheck(userId, resource, action, resourceType, resourceId, context);
        if (cacheable) {
            authorizationCache.put(key, result);
        }
        return result;
    }

    private boolean doCheck(long userId, String resource, String action, String resourceType, String resourceId,
                            Map<String, Object> context) {
        // 0) 资源实例级授权（L3）：命中即决定性，DENY 优先于 ALLOW
        Boolean granted = decideByInstanceGrant(userId, action, resourceType, resourceId);
        if (granted != null) {
            return granted;
        }
        // 1) ABAC 策略优先：首个「资源 / 动作匹配且条件满足」的策略决定结果
        for (PermPolicy policy : policyMapper.selectEnabledOrderByPriority()) {
            if (!matchesResource(policy.getResource(), resource) || !matchesAction(policy.getAction(), action)) {
                continue;
            }
            if (!conditionEvaluator.evaluate(policy.getConditions(), context)) {
                continue;
            }
            return policy.getEffect() != null && policy.getEffect() == PermPolicy.EFFECT_ALLOW;
        }
        // 2) 回退 RBAC：用户的有效权限集合
        for (PermPermission permission : permissionMapper.selectEffectiveByUserId(userId)) {
            if (matchesResource(permission.getResource(), resource) && matchesAction(permission.getAction(), action)) {
                return true;
            }
        }
        return false;
    }

    private String cacheKey(long userId, String resource, String action, String resourceType, String resourceId) {
        return userId + "|" + resource + "|" + action + "|" + resourceType + "|" + resourceId;
    }

    /**
     * 资源实例级授权判定。
     *
     * @return {@code TRUE} 允许 / {@code FALSE} 拒绝 / {@code null} 无适用授权（继续后续判定）
     */
    private Boolean decideByInstanceGrant(long userId, String action, String resourceType, String resourceId) {
        if (resourceType == null || resourceType.isBlank() || resourceId == null || resourceId.isBlank()) {
            return null;
        }
        List<PermResourceGrant> grants = grantMapper.selectActiveByResource(resourceType, resourceId);
        if (grants.isEmpty()) {
            return null;
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        Long deptId = userDeptMapper.selectDeptIdByUserId(userId);
        boolean allowSeen = false;
        for (PermResourceGrant grant : grants) {
            if (!matchesAction(grant.getAction(), action) || !matchesSubject(grant, userId, roleIds, deptId)) {
                continue;
            }
            if (grant.getEffect() != null && grant.getEffect() == PermResourceGrant.EFFECT_DENY) {
                return Boolean.FALSE;
            }
            allowSeen = true;
        }
        return allowSeen ? Boolean.TRUE : null;
    }

    private boolean matchesSubject(PermResourceGrant grant, long userId, List<Long> roleIds, Long deptId) {
        Integer subjectType = grant.getSubjectType();
        Long subjectId = grant.getSubjectId();
        if (subjectType == null || subjectId == null) {
            return false;
        }
        return switch (subjectType) {
            case PermResourceGrant.SUBJECT_USER -> subjectId == userId;
            case PermResourceGrant.SUBJECT_ROLE -> roleIds.contains(subjectId);
            case PermResourceGrant.SUBJECT_DEPT -> deptId != null && deptId.equals(subjectId);
            default -> false;
        };
    }

    private boolean matchesResource(String pattern, String resource) {
        return pattern != null && resource != null && pathMatcher.match(pattern, resource);
    }

    private boolean matchesAction(String pattern, String action) {
        return pattern != null && (PermPolicy.ACTION_ANY.equals(pattern) || pattern.equals(action));
    }
}
