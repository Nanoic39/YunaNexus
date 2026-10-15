package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.permission.abac.AbacConditionEvaluator;
import cc.nanoic.yunanexus.permission.cache.AuthorizationCache;
import cc.nanoic.yunanexus.permission.config.PermissionCacheProperties;
import cc.nanoic.yunanexus.permission.entity.PermPermission;
import cc.nanoic.yunanexus.permission.entity.PermPolicy;
import cc.nanoic.yunanexus.permission.entity.PermResourceGrant;
import cc.nanoic.yunanexus.permission.mapper.PermPermissionMapper;
import cc.nanoic.yunanexus.permission.mapper.PermPolicyMapper;
import cc.nanoic.yunanexus.permission.mapper.PermResourceGrantMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthorizationServiceImpl} 单元测试。<p>
 *
 * 覆盖：资源实例级授权（L3）优先与 DENY 优先、ABAC 策略优先、条件不满足回退 RBAC、RBAC 命中与默认拒绝。
 *
 * @author yunanexus
 */
class AuthorizationServiceImplTest {

    private static final long USER_ID = 1L;
    private static final String RESOURCE = "/api/v1/file/upload";
    private static final String ACTION = "write";

    private PermPolicyMapper policyMapper;
    private PermPermissionMapper permissionMapper;
    private PermResourceGrantMapper grantMapper;
    private PermUserRoleMapper userRoleMapper;
    private PermUserDeptMapper userDeptMapper;
    private AuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        policyMapper = mock(PermPolicyMapper.class);
        permissionMapper = mock(PermPermissionMapper.class);
        grantMapper = mock(PermResourceGrantMapper.class);
        userRoleMapper = mock(PermUserRoleMapper.class);
        userDeptMapper = mock(PermUserDeptMapper.class);
        PermissionCacheProperties cacheProperties = new PermissionCacheProperties();
        cacheProperties.setEnabled(false);
        service = new AuthorizationServiceImpl(policyMapper, permissionMapper, grantMapper,
                userRoleMapper, userDeptMapper, new AbacConditionEvaluator(new ObjectMapper()),
                new AuthorizationCache(cacheProperties));
    }

    private PermPolicy policy(String resource, String action, int effect, String conditions) {
        PermPolicy policy = new PermPolicy();
        policy.setResource(resource);
        policy.setAction(action);
        policy.setEffect(effect);
        policy.setConditions(conditions);
        return policy;
    }

    private PermPermission permission(String resource, String action) {
        PermPermission permission = new PermPermission();
        permission.setResource(resource);
        permission.setAction(action);
        return permission;
    }

    private PermResourceGrant grant(int subjectType, long subjectId, String action, int effect) {
        PermResourceGrant grant = new PermResourceGrant();
        grant.setSubjectType(subjectType);
        grant.setSubjectId(subjectId);
        grant.setResourceType("file");
        grant.setResourceId("100");
        grant.setAction(action);
        grant.setEffect(effect);
        return grant;
    }

    private boolean checkInstance() {
        return service.check(USER_ID, RESOURCE, ACTION, "file", "100", Map.of());
    }

    @Test
    void 无策略时回退RBAC命中() {
        when(policyMapper.selectEnabledOrderByPriority()).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(USER_ID))
                .thenReturn(List.of(permission("/api/v1/file/**", "write")));

        assertTrue(service.check(USER_ID, RESOURCE, ACTION, Map.of()));
    }

    @Test
    void 无策略且RBAC未命中则拒绝() {
        when(policyMapper.selectEnabledOrderByPriority()).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(USER_ID)).thenReturn(List.of());

        assertFalse(service.check(USER_ID, RESOURCE, ACTION, Map.of()));
    }

    @Test
    void 动作不匹配则拒绝() {
        when(policyMapper.selectEnabledOrderByPriority()).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(USER_ID))
                .thenReturn(List.of(permission("/api/v1/file/**", "read")));

        assertFalse(service.check(USER_ID, RESOURCE, ACTION, Map.of()));
    }

    @Test
    void 策略DENY优先于RBAC允许() {
        when(policyMapper.selectEnabledOrderByPriority())
                .thenReturn(List.of(policy("/api/v1/file/**", "write", PermPolicy.EFFECT_DENY, null)));
        when(permissionMapper.selectEffectiveByUserId(anyLong()))
                .thenReturn(List.of(permission("/api/v1/file/**", "write")));

        assertFalse(service.check(USER_ID, RESOURCE, ACTION, Map.of()));
    }

    @Test
    void 策略ALLOW无需RBAC授权() {
        when(policyMapper.selectEnabledOrderByPriority())
                .thenReturn(List.of(policy("/api/v1/file/**", "write", PermPolicy.EFFECT_ALLOW, null)));
        when(permissionMapper.selectEffectiveByUserId(anyLong())).thenReturn(List.of());

        assertTrue(service.check(USER_ID, RESOURCE, ACTION, Map.of()));
    }

    @Test
    void 策略通配动作与条件满足时命中() {
        when(policyMapper.selectEnabledOrderByPriority())
                .thenReturn(List.of(policy("/api/v1/file/**", PermPolicy.ACTION_ANY, PermPolicy.EFFECT_ALLOW,
                        "[{\"attribute\":\"ip\",\"operator\":\"EQ\",\"value\":\"10.0.0.1\"}]")));
        when(permissionMapper.selectEffectiveByUserId(anyLong())).thenReturn(List.of());

        assertTrue(service.check(USER_ID, RESOURCE, "delete", Map.of("ip", "10.0.0.1")));
    }

    @Test
    void 策略条件不满足时回退RBAC() {
        when(policyMapper.selectEnabledOrderByPriority())
                .thenReturn(List.of(policy("/api/v1/file/**", "write", PermPolicy.EFFECT_ALLOW,
                        "[{\"attribute\":\"ip\",\"operator\":\"EQ\",\"value\":\"10.0.0.1\"}]")));
        when(permissionMapper.selectEffectiveByUserId(USER_ID)).thenReturn(List.of());

        assertFalse(service.check(USER_ID, RESOURCE, ACTION, Map.of("ip", "10.0.0.2")));
    }

    @Test
    void 策略资源不匹配时回退RBAC() {
        when(policyMapper.selectEnabledOrderByPriority())
                .thenReturn(List.of(policy("/api/v1/other/**", "write", PermPolicy.EFFECT_DENY, null)));
        when(permissionMapper.selectEffectiveByUserId(USER_ID))
                .thenReturn(List.of(permission("/api/v1/file/**", "write")));

        assertTrue(service.check(USER_ID, RESOURCE, ACTION, Map.of()));
    }

    @Test
    void 实例授权用户主体ALLOW命中无需RBAC() {
        when(grantMapper.selectActiveByResource("file", "100"))
                .thenReturn(List.of(grant(PermResourceGrant.SUBJECT_USER, USER_ID, "write",
                        PermResourceGrant.EFFECT_ALLOW)));

        assertTrue(checkInstance());
    }

    @Test
    void 实例授权DENY优先于ALLOW与RBAC() {
        when(grantMapper.selectActiveByResource("file", "100"))
                .thenReturn(List.of(
                        grant(PermResourceGrant.SUBJECT_USER, USER_ID, "write", PermResourceGrant.EFFECT_ALLOW),
                        grant(PermResourceGrant.SUBJECT_USER, USER_ID, "write", PermResourceGrant.EFFECT_DENY)));

        assertFalse(checkInstance());
    }

    @Test
    void 实例授权角色主体命中() {
        when(grantMapper.selectActiveByResource("file", "100"))
                .thenReturn(List.of(grant(PermResourceGrant.SUBJECT_ROLE, 5L, "*", PermResourceGrant.EFFECT_ALLOW)));
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(5L));

        assertTrue(checkInstance());
    }

    @Test
    void 实例授权部门主体命中() {
        when(grantMapper.selectActiveByResource("file", "100"))
                .thenReturn(List.of(grant(PermResourceGrant.SUBJECT_DEPT, 7L, "write", PermResourceGrant.EFFECT_ALLOW)));
        when(userDeptMapper.selectDeptIdByUserId(USER_ID)).thenReturn(7L);

        assertTrue(checkInstance());
    }

    @Test
    void 实例授权主体不匹配时回退RBAC() {
        when(grantMapper.selectActiveByResource("file", "100"))
                .thenReturn(List.of(grant(PermResourceGrant.SUBJECT_ROLE, 99L, "write",
                        PermResourceGrant.EFFECT_DENY)));
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(5L));
        when(policyMapper.selectEnabledOrderByPriority()).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(USER_ID))
                .thenReturn(List.of(permission("/api/v1/file/**", "write")));

        assertTrue(checkInstance());
    }

    @Test
    void 实例授权动作不匹配时回退RBAC() {
        when(grantMapper.selectActiveByResource("file", "100"))
                .thenReturn(List.of(grant(PermResourceGrant.SUBJECT_USER, USER_ID, "read",
                        PermResourceGrant.EFFECT_DENY)));
        when(policyMapper.selectEnabledOrderByPriority()).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(USER_ID))
                .thenReturn(List.of(permission("/api/v1/file/**", "write")));

        assertTrue(checkInstance());
    }

    @Test
    void 无实例授权时按原逻辑回退() {
        when(grantMapper.selectActiveByResource(anyString(), anyString())).thenReturn(List.of());
        when(policyMapper.selectEnabledOrderByPriority()).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(USER_ID)).thenReturn(List.of());

        assertFalse(checkInstance());
    }

    @Test
    void 无上下文判定命中缓存不重复查库() {
        AuthorizationServiceImpl cachedService = new AuthorizationServiceImpl(policyMapper, permissionMapper,
                grantMapper, userRoleMapper, userDeptMapper, new AbacConditionEvaluator(new ObjectMapper()),
                new AuthorizationCache(new PermissionCacheProperties()));
        when(policyMapper.selectEnabledOrderByPriority()).thenReturn(List.of());
        when(permissionMapper.selectEffectiveByUserId(USER_ID))
                .thenReturn(List.of(permission("/api/v1/file/**", "write")));

        assertTrue(cachedService.check(USER_ID, RESOURCE, ACTION, Map.of()));
        assertTrue(cachedService.check(USER_ID, RESOURCE, ACTION, Map.of()));

        verify(policyMapper, times(1)).selectEnabledOrderByPriority();
        verify(permissionMapper, times(1)).selectEffectiveByUserId(USER_ID);
    }
}
