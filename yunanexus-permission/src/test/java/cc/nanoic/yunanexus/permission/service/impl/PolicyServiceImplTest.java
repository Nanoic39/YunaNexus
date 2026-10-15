package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.abac.AbacConditionEvaluator;
import cc.nanoic.yunanexus.permission.domain.PolicySaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPolicy;
import cc.nanoic.yunanexus.permission.mapper.PermPolicyMapper;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PolicyServiceImpl} 单元测试。
 *
 * @author yunanexus
 */
class PolicyServiceImplTest {

    private PermPolicyMapper policyMapper;
    private PolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        policyMapper = mock(PermPolicyMapper.class);
        service = new PolicyServiceImpl(policyMapper, new AbacConditionEvaluator(new ObjectMapper()));
    }

    private PolicySaveRequest request(String conditions) {
        PolicySaveRequest request = new PolicySaveRequest();
        request.setPolicyName("策略");
        request.setResource("/api/v1/file/**");
        request.setAction("*");
        request.setEffect(PermPolicy.EFFECT_ALLOW);
        request.setConditions(conditions);
        return request;
    }

    @Test
    void 新增策略默认优先级与启用状态() {
        when(policyMapper.insert(any(PermPolicy.class))).thenReturn(1);
        PermPolicy persisted = new PermPolicy();
        persisted.setId(1L);
        when(policyMapper.selectById(any())).thenReturn(persisted);

        service.create(request("[{\"attribute\":\"ip\",\"operator\":\"EXISTS\"}]"));

        verify(policyMapper).insert(any(PermPolicy.class));
    }

    @Test
    void 新增策略条件非法时拒绝且不落库() {
        BizException ex = assertThrows(BizException.class, () -> service.create(request("{not-json")));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(policyMapper, never()).insert(any(PermPolicy.class));
    }

    @Test
    void 更新策略不存在抛出业务异常() {
        when(policyMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.update(1L, request(null)));
        assertEquals(ErrorCode.POLICY_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 删除策略不存在抛出业务异常() {
        when(policyMapper.selectById(1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.delete(1L));
        assertEquals(ErrorCode.POLICY_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void 更新策略写入全部字段() {
        PermPolicy existing = new PermPolicy();
        existing.setId(1L);
        when(policyMapper.selectById(1L)).thenReturn(existing);
        when(policyMapper.updateById(any(PermPolicy.class))).thenReturn(1);

        PolicySaveRequest request = request(null);
        request.setPriority(5);
        request.setEffect(PermPolicy.EFFECT_DENY);
        service.update(1L, request);

        assertEquals(5, existing.getPriority());
        assertEquals(PermPolicy.EFFECT_DENY, existing.getEffect());
        verify(policyMapper).updateById(existing);
    }
}
