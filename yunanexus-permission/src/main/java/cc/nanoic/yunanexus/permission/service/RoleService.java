package cc.nanoic.yunanexus.permission.service;

import cc.nanoic.yunanexus.permission.domain.RoleSaveRequest;
import cc.nanoic.yunanexus.permission.entity.PermRole;

import java.util.List;

/**
 * 角色管理服务（RBAC）。
 *
 * @author yunanexus
 */
public interface RoleService {

    /** 新增角色。 */
    PermRole create(RoleSaveRequest request);

    /** 更新角色（{@code roleCode} 不可变，仅更新名称 / 描述 / 状态）。 */
    PermRole update(long id, RoleSaveRequest request);

    /** 删除角色，并清理其绑定关系。 */
    void delete(long id);

    /** 按 ID 查询角色。 */
    PermRole getById(long id);

    /** 查询全部角色（按 ID 升序）。 */
    List<PermRole> list();
}
