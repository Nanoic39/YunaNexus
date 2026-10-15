package cc.nanoic.yunanexus.permission.service.impl;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.permission.domain.FieldCheckResponse;
import cc.nanoic.yunanexus.permission.domain.FieldRuleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermFieldRule;
import cc.nanoic.yunanexus.permission.mapper.PermFieldRuleMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserDeptMapper;
import cc.nanoic.yunanexus.permission.mapper.PermUserRoleMapper;
import cc.nanoic.yunanexus.permission.service.FieldRuleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 字段级权限服务实现。
 *
 * @author yunanexus
 */
@Service
public class FieldRuleServiceImpl implements FieldRuleService {

    private final PermFieldRuleMapper fieldRuleMapper;
    private final PermUserRoleMapper userRoleMapper;
    private final PermUserDeptMapper userDeptMapper;

    public FieldRuleServiceImpl(PermFieldRuleMapper fieldRuleMapper,
                                PermUserRoleMapper userRoleMapper,
                                PermUserDeptMapper userDeptMapper) {
        this.fieldRuleMapper = fieldRuleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userDeptMapper = userDeptMapper;
    }

    @Override
    public PermFieldRule create(FieldRuleSaveRequest request) {
        if (exists(request, null)) {
            throw new BizException(ErrorCode.FIELD_RULE_EXISTS);
        }
        PermFieldRule rule = new PermFieldRule();
        apply(rule, request);
        fieldRuleMapper.insert(rule);
        return fieldRuleMapper.selectById(rule.getId());
    }

    @Override
    public PermFieldRule update(long id, FieldRuleSaveRequest request) {
        PermFieldRule rule = requireRule(id);
        if (exists(request, id)) {
            throw new BizException(ErrorCode.FIELD_RULE_EXISTS);
        }
        apply(rule, request);
        fieldRuleMapper.updateById(rule);
        return fieldRuleMapper.selectById(id);
    }

    @Override
    public void delete(long id) {
        requireRule(id);
        fieldRuleMapper.deleteById(id);
    }

    @Override
    public PermFieldRule getById(long id) {
        return requireRule(id);
    }

    @Override
    public List<PermFieldRule> list(Integer subjectType, Long subjectId, String resourceType,
                                    String fieldName, Integer access) {
        return fieldRuleMapper.selectList(new LambdaQueryWrapper<PermFieldRule>()
                .eq(subjectType != null, PermFieldRule::getSubjectType, subjectType)
                .eq(subjectId != null, PermFieldRule::getSubjectId, subjectId)
                .eq(resourceType != null, PermFieldRule::getResourceType, resourceType)
                .eq(fieldName != null, PermFieldRule::getFieldName, fieldName)
                .eq(access != null, PermFieldRule::getAccess, access)
                .orderByAsc(PermFieldRule::getResourceType)
                .orderByAsc(PermFieldRule::getFieldName)
                .orderByAsc(PermFieldRule::getId));
    }

    @Override
    public FieldCheckResponse resolve(long userId, String resourceType, int access) {
        List<PermFieldRule> rules = fieldRuleMapper.selectEnabledByResourceAccess(resourceType, access);
        if (rules.isEmpty()) {
            return FieldCheckResponse.of(userId, resourceType, access, false, List.of());
        }
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        Long deptId = userDeptMapper.selectDeptIdByUserId(userId);
        Set<String> allowed = new LinkedHashSet<>();
        Set<String> denied = new HashSet<>();
        boolean restricted = false;
        for (PermFieldRule rule : rules) {
            if (!matchesSubject(rule, userId, roleIds, deptId)) {
                continue;
            }
            restricted = true;
            if (rule.getEffect() != null && rule.getEffect() == PermFieldRule.EFFECT_DENY) {
                denied.add(rule.getFieldName());
            } else {
                allowed.add(rule.getFieldName());
            }
        }
        if (!restricted) {
            return FieldCheckResponse.of(userId, resourceType, access, false, List.of());
        }
        allowed.removeAll(denied);
        return FieldCheckResponse.of(userId, resourceType, access, true, List.copyOf(allowed));
    }

    private boolean matchesSubject(PermFieldRule rule, long userId, List<Long> roleIds, Long deptId) {
        Integer subjectType = rule.getSubjectType();
        Long subjectId = rule.getSubjectId();
        if (subjectType == null || subjectId == null) {
            return false;
        }
        return switch (subjectType) {
            case PermFieldRule.SUBJECT_USER -> subjectId == userId;
            case PermFieldRule.SUBJECT_ROLE -> roleIds.contains(subjectId);
            case PermFieldRule.SUBJECT_DEPT -> deptId != null && deptId.equals(subjectId);
            default -> false;
        };
    }

    /** 唯一性判定：同一「主体 + 资源类型 + 字段 + 访问类型」仅允许一条。 */
    private boolean exists(FieldRuleSaveRequest request, Long excludeId) {
        LambdaQueryWrapper<PermFieldRule> wrapper = new LambdaQueryWrapper<PermFieldRule>()
                .eq(PermFieldRule::getSubjectType, request.getSubjectType())
                .eq(PermFieldRule::getSubjectId, request.getSubjectId())
                .eq(PermFieldRule::getResourceType, request.getResourceType())
                .eq(PermFieldRule::getFieldName, request.getFieldName())
                .eq(PermFieldRule::getAccess, request.getAccess());
        if (excludeId != null) {
            wrapper.ne(PermFieldRule::getId, excludeId);
        }
        return fieldRuleMapper.selectCount(wrapper) > 0;
    }

    private void apply(PermFieldRule rule, FieldRuleSaveRequest request) {
        rule.setSubjectType(request.getSubjectType());
        rule.setSubjectId(request.getSubjectId());
        rule.setResourceType(request.getResourceType());
        rule.setFieldName(request.getFieldName());
        rule.setAccess(request.getAccess());
        rule.setEffect(request.getEffect());
        rule.setStatus(request.getStatus() == null ? PermFieldRule.STATUS_ENABLED : request.getStatus());
    }

    private PermFieldRule requireRule(long id) {
        PermFieldRule rule = fieldRuleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ErrorCode.FIELD_RULE_NOT_FOUND);
        }
        return rule;
    }
}
