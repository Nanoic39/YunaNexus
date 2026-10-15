package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.abac.AbacConditionEvaluator;
import cc.nanoic.yunanexus.permission.domain.PolicySaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPolicy;
import cc.nanoic.yunanexus.permission.mapper.PermPolicyMapper;
import cc.nanoic.yunanexus.permission.service.PolicyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ABAC 策略管理服务实现。
 *
 * @author yunanexus
 */
@Service
public class PolicyServiceImpl implements PolicyService {

    private final PermPolicyMapper policyMapper;
    private final AbacConditionEvaluator conditionEvaluator;

    public PolicyServiceImpl(PermPolicyMapper policyMapper, AbacConditionEvaluator conditionEvaluator) {
        this.policyMapper = policyMapper;
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public PermPolicy create(PolicySaveRequest request) {
        conditionEvaluator.validate(request.getConditions());
        PermPolicy policy = new PermPolicy();
        apply(policy, request);
        policyMapper.insert(policy);
        return policyMapper.selectById(policy.getId());
    }

    @Override
    public PermPolicy update(long id, PolicySaveRequest request) {
        PermPolicy policy = requirePolicy(id);
        conditionEvaluator.validate(request.getConditions());
        apply(policy, request);
        policyMapper.updateById(policy);
        return policyMapper.selectById(id);
    }

    @Override
    public void delete(long id) {
        requirePolicy(id);
        policyMapper.deleteById(id);
    }

    @Override
    public PermPolicy getById(long id) {
        return requirePolicy(id);
    }

    @Override
    public List<PermPolicy> list() {
        return policyMapper.selectList(new LambdaQueryWrapper<PermPolicy>()
                .orderByDesc(PermPolicy::getPriority)
                .orderByAsc(PermPolicy::getId));
    }

    private void apply(PermPolicy policy, PolicySaveRequest request) {
        policy.setPolicyName(request.getPolicyName());
        policy.setResource(request.getResource());
        policy.setAction(request.getAction());
        policy.setEffect(request.getEffect());
        policy.setConditions(request.getConditions());
        policy.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        policy.setStatus(request.getStatus() == null ? PermPolicy.STATUS_ENABLED : request.getStatus());
    }

    private PermPolicy requirePolicy(long id) {
        PermPolicy policy = policyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(ErrorCode.POLICY_NOT_FOUND);
        }
        return policy;
    }
}
