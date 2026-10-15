package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermResourceGrant;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 资源实例级授权 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermResourceGrantMapper extends BaseMapper<PermResourceGrant> {

    /**
     * 查询指定资源实例的全部生效授权（启用且未过期）。<p>
     * 主体过滤交由服务层按用户 / 角色 / 部门集合判定，避免动态 IN 拼接。
     *
     * @param resourceType 资源类型
     * @param resourceId   资源实例 ID
     * @return 生效授权
     */
    @Select("SELECT * FROM perm_resource_grant WHERE status = 1 AND resource_type = #{resourceType} "
            + "AND resource_id = #{resourceId} AND (expire_time IS NULL OR expire_time > NOW())")
    List<PermResourceGrant> selectActiveByResource(@Param("resourceType") String resourceType,
                                                   @Param("resourceId") String resourceId);
}
