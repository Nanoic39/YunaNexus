package cc.nanoic.yunanexus.permission.domain;

import cc.nanoic.yunanexus.permission.entity.PermRole;
import lombok.Data;

/**
 * 角色简要信息（当前用户角色清单，供前端 RBAC 判定管理端入口）。<p>
 *
 * 仅暴露识别与展示所需字段，不含数据范围等管理端内部属性。
 *
 * @author yunanexus
 */
@Data
public class RoleBrief {

    private Long id;
    private String roleCode;
    private String roleName;

    public static RoleBrief from(PermRole role) {
        RoleBrief brief = new RoleBrief();
        brief.setId(role.getId());
        brief.setRoleCode(role.getRoleCode());
        brief.setRoleName(role.getRoleName());
        return brief;
    }
}
