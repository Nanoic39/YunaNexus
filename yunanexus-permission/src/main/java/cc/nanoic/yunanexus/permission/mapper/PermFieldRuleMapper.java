package cc.nanoic.yunanexus.permission.mapper;

import cc.nanoic.yunanexus.permission.entity.PermFieldRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 字段级权限规则 Mapper。
 *
 * @author yunanexus
 */
@Mapper
public interface PermFieldRuleMapper extends BaseMapper<PermFieldRule> {

    /**
     * 查询指定资源类型 / 访问类型的全部启用规则。<p>
     * 主体过滤交由服务层按用户 / 角色 / 部门集合判定，避免动态 IN 拼接。
     *
     * @param resourceType 资源类型
     * @param access       访问类型（1-读 2-写）
     * @return 启用规则
     */
    @Select("SELECT * FROM perm_field_rule WHERE status = 1 AND resource_type = #{resourceType} "
            + "AND access = #{access}")
    List<PermFieldRule> selectEnabledByResourceAccess(@Param("resourceType") String resourceType,
                                                      @Param("access") int access);
}
