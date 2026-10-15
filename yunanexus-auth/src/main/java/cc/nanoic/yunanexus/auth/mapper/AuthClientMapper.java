package cc.nanoic.yunanexus.auth.mapper;

import cc.nanoic.yunanexus.auth.entity.AuthClient;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * SSO 客户端 Mapper。<p>
 *
 * 登录时校验客户端是否存在、是否启用，并根据 {@code redirect_uri} 完成跳转。
 *
 * @author yunanexus
 */
@Mapper
public interface AuthClientMapper extends BaseMapper<AuthClient> {

    /**
     * 按客户端唯一标识检索。
     *
     * @param clientCode 客户端标识
     * @return 客户端，不存在返回 null
     */
    @Select("SELECT * FROM auth_client WHERE client_code = #{clientCode}")
    AuthClient selectByClientCode(@Param("clientCode") String clientCode);
}
