package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.PolicySaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPolicy;

import java.util.List;

/**
 * ABAC 策略管理服务。
 *
 * @author yunanexus
 */
public interface PolicyService {

    /** 新增策略。 */
    PermPolicy create(PolicySaveRequest request);

    /** 更新策略。 */
    PermPolicy update(long id, PolicySaveRequest request);

    /** 删除策略。 */
    void delete(long id);

    /** 按 ID 查询策略。 */
    PermPolicy getById(long id);

    /** 查询全部策略（按优先级降序，同优先级按 ID 升序）。 */
    List<PermPolicy> list();
}
