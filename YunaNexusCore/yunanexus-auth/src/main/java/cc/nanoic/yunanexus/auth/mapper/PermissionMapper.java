package cc.nanoic.yunanexus.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper {

    /**
     * 查询用户已授权的最大角色等级
     * 
     * @param userId
     * @return
     */
    @Select("SELECT MAX(r.`level`) FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND ur.status = 1 AND r.status = 1")
    Integer findMaxRoleLevel(@Param("userId") Long userId);

    /**
     * 查询用户已授权的资源编码
     * 
     * @param userId
     * @return
     */
    @Select("SELECT DISTINCT rs.code FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id AND r.status = 1 JOIN related_roles_resources_fields_rules rr ON rr.role_id = r.id AND rr.status = 1 JOIN resources rs ON rs.id = rr.resource_id AND rs.status = 1 WHERE ur.user_id = #{userId} AND ur.status = 1")
    List<String> listPermissionCodes(@Param("userId") Long userId);

    /**
     * 查询用户已授权的角色名称
     * 
     * @param userId
     * @return
     */
    @Select("SELECT r.name FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND ur.status = 1 AND r.status = 1")
    List<String> listRoleNames(@Param("userId") Long userId);

    /**
     * 查询用户已授权的资源编码
     * 
     * @param userId
     * @param type
     * @return
     */
    @Select("SELECT DISTINCT rs.code FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id AND r.status = 1 JOIN related_roles_resources_fields_rules rr ON rr.role_id = r.id AND rr.status = 1 JOIN resources rs ON rs.id = rr.resource_id AND rs.status = 1 WHERE ur.user_id = #{userId} AND ur.status = 1 AND rs.type = #{type}")
    List<String> listResourceCodesByType(@Param("userId") Long userId, @Param("type") String type);

    /**
     * 根据资源编码查询资源ID
     * 
     * @param code
     * @return
     */
    @Select("SELECT rs.id FROM resources rs WHERE rs.code = #{code} AND rs.status = 1 LIMIT 1")
    Long findResourceIdByCode(@Param("code") String code);

    /**
     * 插入资源
     *  
     * @param name
     * @param code
     * @param type
     * @param path
     * @param status
     * @return
     */
    @Insert("INSERT INTO resources(name, code, type, path, status) SELECT #{name}, #{code}, #{type}, #{path}, #{status} FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM resources WHERE code = #{code})")
    int insertResourceIfAbsent(@Param("name") String name, @Param("code") String code, @Param("type") String type,
            @Param("path") String path, @Param("status") Integer status);

    /**
     * 插入资源字段
     *  
     * @param resourceId
     * @param fieldName
     * @param description
     * @param status
     * @return
     */
    @Insert("INSERT INTO resource_fields(resource_id, field_name, description, status) SELECT #{resourceId}, #{fieldName}, #{description}, #{status} FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM resource_fields WHERE resource_id = #{resourceId} AND field_name = #{fieldName})")
    int insertResourceFieldIfAbsent(@Param("resourceId") Long resourceId, @Param("fieldName") String fieldName,
            @Param("description") String description, @Param("status") Integer status);

    /**
     * 查询用户是否授权所有字段
     * 
     * @param userId
     * @param resourceId
     * @return
     */
    @Select("SELECT COUNT(1) FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id AND r.status = 1 JOIN related_roles_resources_fields_rules rr ON rr.role_id = r.id AND rr.status = 1 WHERE ur.user_id = #{userId} AND ur.status = 1 AND rr.resource_id = #{resourceId} AND rr.field_ids = '*'")
    Integer countAllFieldsGrant(@Param("userId") Long userId, @Param("resourceId") Long resourceId);

    /**
     * 查询资源的所有字段
     * 
     * @param resourceId
     * @return
     */
    @Select("SELECT DISTINCT rf.field_name FROM resource_fields rf WHERE rf.resource_id = #{resourceId} AND rf.status = 1")
    List<String> listAllFieldNamesByResourceId(@Param("resourceId") Long resourceId);

    /**
     * 查询用户已授权的字段
     * 
     * @param userId
     * @param resourceId
     * @return
     */
    @Select("SELECT DISTINCT rf.field_name FROM related_users_roles ur JOIN roles r ON r.id = ur.role_id AND r.status = 1 JOIN related_roles_resources_fields_rules rr ON rr.role_id = r.id AND rr.status = 1 JOIN resource_fields rf ON rf.resource_id = rr.resource_id AND rf.status = 1 WHERE ur.user_id = #{userId} AND ur.status = 1 AND rr.resource_id = #{resourceId} AND rr.field_ids <> '*' AND FIND_IN_SET(CAST(rf.id AS CHAR), rr.field_ids) > 0")
    List<String> listGrantedFieldNamesByUserIdAndResourceId(@Param("userId") Long userId,
            @Param("resourceId") Long resourceId);

    /**
     * 绑定角色
     * 
     * @param userId
     * @param roleName
     * @return
     */
    @Insert("INSERT INTO related_users_roles(user_id, role_id, status) SELECT #{userId}, r.id, 1 FROM roles r WHERE r.name = #{roleName} AND r.status = 1 AND NOT EXISTS ( SELECT 1 FROM related_users_roles ur WHERE ur.user_id = #{userId} AND ur.role_id = r.id ) LIMIT 1")
    int bindRole(@Param("userId") Long userId, @Param("roleName") String roleName);
}
