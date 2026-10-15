package cc.nanoic.yunanexus.user.mapper;

import cc.nanoic.yunanexus.user.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户资料 Mapper。<p>
 *
 * 所有查询均以 {@code user_id} 为条件，调用前须已由 {@code @ShardRoute} 设定分片路由。
 *
 * @author yunanexus
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按全局用户 ID 查询用户资料。
     *
     * @param userId 全局用户 ID
     * @return 用户资料，不存在返回 {@code null}
     */
    @Select("SELECT * FROM sys_user WHERE user_id = #{userId}")
    SysUser selectByUserId(@Param("userId") long userId);
}
