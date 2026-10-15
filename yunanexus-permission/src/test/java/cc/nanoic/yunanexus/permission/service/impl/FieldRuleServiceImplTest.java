package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.FieldCheckResponse;
import cc.nanoic.yunanexus.permission.domain.FieldRuleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermFieldRule;
import cc.nanoic.yunanexus.permission.mapper.PermFieldRuleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FieldRuleServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class FieldRuleServiceImplTest {

    private static final long USER_ID = 10001L;

    private PermFieldRuleMapper fieldRuleMapper;
    private PermUserRoleMapper userRoleMapper;
    private PermUserDeptMapper userDeptMapper;
    private FieldRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        fieldRuleMapper = mock(PermFieldRuleMapper.class);
        userRoleMapper = mock(PermUserRoleMapper.class);
        userDeptMapper = mock(PermUserDeptMapper.class);
        service = new FieldRuleServiceImpl(fieldRuleMapper, userRoleMapper, userDeptMapper);
    }

    private FieldRuleSaveRequest request() {
        FieldRuleSaveRequest request = new FieldRuleSaveRequest();
        request.setSubjectType(PermFieldRule.SUBJECT_USER);
        request.setSubjectId(USER_ID);
        request.setResourceType("user_profile");
        request.setFieldName("phone");
        request.setAccess(PermFieldRule.ACCESS_READ);
        request.setEffect(PermFieldRule.EFFECT_ALLOW);
        return request;
    }

    private PermFieldRule rule(int subjectType, long subjectId, String field, int effect) {
        PermFieldRule rule = new PermFieldRule();
        rule.setSubjectType(subjectType);
        rule.setSubjectId(subjectId);
        rule.setResourceType("user_profile");
        rule.setFieldName(field);
        rule.setAccess(PermFieldRule.ACCESS_READ);
        rule.setEffect(effect);
        return rule;
    }

    @Test
    void 新增规则默认启用() {
        when(fieldRuleMapper.selectCount(any())).thenReturn(0L);
        when(fieldRuleMapper.insert(any(PermFieldRule.class))).thenAnswer(invocation -> {
            PermFieldRule inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });
        PermFieldRule saved = new PermFieldRule();
        saved.setId(1L);
        saved.setStatus(PermFieldRule.STATUS_ENABLED);
        when(fieldRuleMapper.selectById(1L)).thenReturn(saved);

        PermFieldRule result = service.create(request());

        assertEquals(1L, result.getId());
        assertEquals(PermFieldRule.STATUS_ENABLED, result.getStatus());
    }

    @Test
    void 新增重复规则抛出业务异常() {
        when(fieldRuleMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals(ErrorCode.FIELD_RULE_EXISTS.getCode(), ex.getCode());
        verify(fieldRuleMapper, never()).insert(any(PermFieldRule.class));
    }

    @Test
    void 更新不存在的规则抛出业务异常() {
        when(fieldRuleMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.update(9L, request()));
        assertEquals(ErrorCode.FIELD_RULE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 更新为重复规则抛出业务异常() {
        PermFieldRule existing = new PermFieldRule();
        existing.setId(1L);
        when(fieldRuleMapper.selectById(1L)).thenReturn(existing);
        when(fieldRuleMapper.selectCount(any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service.update(1L, request()));
        assertEquals(ErrorCode.FIELD_RULE_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void 删除不存在的规则抛出业务异常() {
        when(fieldRuleMapper.selectById(9L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete(9L));
        assertEquals(ErrorCode.FIELD_RULE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 无规则时不受字段级限制() {
        when(fieldRuleMapper.selectEnabledByResourceAccess("user_profile", PermFieldRule.ACCESS_READ))
                .thenReturn(List.of());

        FieldCheckResponse response = service.resolve(USER_ID, "user_profile", PermFieldRule.ACCESS_READ);

        assertFalse(response.isRestricted());
        assertTrue(response.getAllowedFields().isEmpty());
    }

    @Test
    void 用户主体允许字段命中() {
        when(fieldRuleMapper.selectEnabledByResourceAccess(anyString(), anyInt()))
                .thenReturn(List.of(rule(PermFieldRule.SUBJECT_USER, USER_ID, "phone", PermFieldRule.EFFECT_ALLOW)));

        FieldCheckResponse response = service.resolve(USER_ID, "user_profile", PermFieldRule.ACCESS_READ);

        assertTrue(response.isRestricted());
        assertEquals(List.of("phone"), response.getAllowedFields());
    }

    @Test
    void 拒绝字段从允许集移除() {
        when(fieldRuleMapper.selectEnabledByResourceAccess(anyString(), anyInt()))
                .thenReturn(List.of(
                        rule(PermFieldRule.SUBJECT_USER, USER_ID, "phone", PermFieldRule.EFFECT_ALLOW),
                        rule(PermFieldRule.SUBJECT_USER, USER_ID, "email", PermFieldRule.EFFECT_ALLOW),
                        rule(PermFieldRule.SUBJECT_ROLE, 5L, "email", PermFieldRule.EFFECT_DENY)));
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(5L));

        FieldCheckResponse response = service.resolve(USER_ID, "user_profile", PermFieldRule.ACCESS_READ);

        assertTrue(response.isRestricted());
        assertEquals(List.of("phone"), response.getAllowedFields());
    }

    @Test
    void 主体不匹配时不受字段级限制() {
        when(fieldRuleMapper.selectEnabledByResourceAccess(anyString(), anyInt()))
                .thenReturn(List.of(rule(PermFieldRule.SUBJECT_ROLE, 99L, "phone", PermFieldRule.EFFECT_DENY)));
        when(userRoleMapper.selectRoleIdsByUserId(USER_ID)).thenReturn(List.of(5L));

        FieldCheckResponse response = service.resolve(USER_ID, "user_profile", PermFieldRule.ACCESS_READ);

        assertFalse(response.isRestricted());
    }

    @Test
    void 条件查询透传Mapper() {
        List<PermFieldRule> expected = List.of(new PermFieldRule());
        when(fieldRuleMapper.selectList(any())).thenReturn(expected);

        List<PermFieldRule> result = service.list(PermFieldRule.SUBJECT_USER, USER_ID, "user_profile", "phone",
                PermFieldRule.ACCESS_READ);

        assertEquals(expected, result);
        verify(fieldRuleMapper).selectList(any());
    }
}
