package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.PermissionSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermPermission;

import java.util.List;

/**
 * 权限管理服务。
 *
 * @author yunanexus
 */
public interface PermissionService {

    /** 新增权限。 */
    PermPermission create(PermissionSaveRequest request);

    /** 更新权限（{@code permCode} 不可变，仅更新资源 / 动作 / 描述 / 状态）。 */
    PermPermission update(long id, PermissionSaveRequest request);

    /** 删除权限，并清理角色绑定关系。 */
    void delete(long id);

    /** 按 ID 查询权限。 */
    PermPermission getById(long id);

    /** 查询全部权限（按 ID 升序）。 */
    List<PermPermission> list();
}
