package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.ResourceGrantSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermResourceGrant;

import java.util.List;

/**
 * 资源实例级授权管理服务（Phase B）。
 *
 * @author yunanexus
 */
public interface ResourceGrantService {

    PermResourceGrant create(ResourceGrantSaveRequest request);

    PermResourceGrant update(long id, ResourceGrantSaveRequest request);

    void delete(long id);

    PermResourceGrant getById(long id);

    /**
     * 条件查询授权（所有参数可选，为空表示不过滤）。
     *
     * @param subjectType  主体类型
     * @param subjectId    主体 ID
     * @param resourceType 资源类型
     * @param resourceId   资源实例 ID
     * @return 授权列表
     */
    List<PermResourceGrant> list(Integer subjectType, Long subjectId, String resourceType, String resourceId);
}
