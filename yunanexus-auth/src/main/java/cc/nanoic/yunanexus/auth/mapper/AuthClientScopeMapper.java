package cc.nanoic.yunanexus.auth.mapper;

import cc.nanoic.yunanexus.auth.entity.AuthClientScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 客户端-权限关联 Mapper。<p>
 *
 * 查询某客户端被授予的权限 ID 集合，供登录后签发带 scope 的令牌使用。
 *
 * @author yunanexus
 */
@Mapper
public interface AuthClientScopeMapper extends BaseMapper<AuthClientScope> {

    /**
     * 查询客户端的全部权限 ID。
     *
     * @param clientCode 客户端标识
     * @return 权限 ID 列表（可能为空）
     */
    @Select("SELECT permission_id FROM auth_client_scope WHERE client_code = #{clientCode}")
    List<Long> selectPermissionIdsByClientCode(@Param("clientCode") String clientCode);
}
