package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.FieldCheckResponse;
import cc.nanoic.yunanexus.permission.domain.FieldRuleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermFieldRule;

import java.util.List;

/**
 * 字段级权限服务（Phase C）。
 *
 * @author yunanexus
 */
public interface FieldRuleService {

    PermFieldRule create(FieldRuleSaveRequest request);

    PermFieldRule update(long id, FieldRuleSaveRequest request);

    void delete(long id);

    PermFieldRule getById(long id);

    /**
     * 条件查询字段规则（所有参数可选，为空表示不过滤）。
     *
     * @param subjectType  主体类型
     * @param subjectId    主体 ID
     * @param resourceType 资源类型
     * @param fieldName    字段名
     * @param access       访问类型（1-读 2-写）
     * @return 字段规则列表
     */
    List<PermFieldRule> list(Integer subjectType, Long subjectId, String resourceType, String fieldName, Integer access);

    /**
     * 解析用户在指定资源类型 / 访问类型下可访问的字段集合。
     *
     * @param userId       全局用户 ID
     * @param resourceType 资源类型
     * @param access       访问类型（1-读 2-写）
     * @return 字段级校验结果
     */
    FieldCheckResponse resolve(long userId, String resourceType, int access);
}
