package cc.nanoic.yunanexus.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper {

    @Select("SELECT MAX(r.`level`) FROM related_users_roles rur JOIN roles r ON r.id = rur.role_id WHERE rur.user_id = #{userId} AND rur.status = 1 AND r.status = 1")
    Integer findMaxRoleLevel(@Param("userId") Long userId);

    @Select("SELECT DISTINCT rs.code FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id AND r.status = 1 JOIN related_roles_resources_fields_rules rr ON rr.role_id = r.id AND rr.status = 1 JOIN resources rs ON rs.id = rr.resource_id AND rs.status = 1 WHERE ur.user_id = #{userId} AND ur.status = 1")
    List<String> listPermissionCodes(@Param("userId") Long userId);

    @Select("SELECT r.name FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND ur.status = 1 AND r.status = 1")
    List<String> listRoleNames(@Param("userId") Long userId);

    @Insert("INSERT INTO related_users_roles(user_id, role_id, status) SELECT #{userId}, r.id, 1 FROM roles r WHERE r.name = #{roleName} AND r.status = 1 AND NOT EXISTS ( SELECT 1 FROM related_users_roles ur WHERE ur.user_id = #{userId} AND ur.role_id = r.id ) LIMIT 1")
    int bindRole(@Param("userId") Long userId, @Param("roleName") String roleName);
}
