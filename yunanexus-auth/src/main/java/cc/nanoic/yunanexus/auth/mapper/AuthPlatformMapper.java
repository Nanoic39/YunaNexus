package cc.nanoic.yunanexus.auth.mapper;

import cc.nanoic.yunanexus.auth.entity.AuthPlatform;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户-客户端授权关系 Mapper。<p>
 *
 * 记录用户对某客户端（平台）的授权，登录时校验授权是否有效，支持撤销 / 停用。
 *
 * @author yunanexus
 */
@Mapper
public interface AuthPlatformMapper extends BaseMapper<AuthPlatform> {

    /**
     * 按用户 + 客户端检索授权记录。
     *
     * @param userId     全局用户 ID
     * @param clientCode 客户端标识
     * @return 授权记录，不存在返回 null
     */
    @Select("SELECT * FROM auth_platform WHERE user_id = #{userId} AND client_code = #{clientCode}")
    AuthPlatform selectByUserIdAndClient(@Param("userId") long userId, @Param("clientCode") String clientCode);

    /**
     * 更新授权状态（授权 / 撤销 / 停用）。<p>
     *
     * 授权时间 {@code authorized_at} 仅在首次授权（status 置 1）时写入，避免撤销后再授权
     * 被旧时间覆盖。
     *
     * @param userId     全局用户 ID
     * @param clientCode 客户端标识
     * @param status     目标状态（1-正常 2-停用 0-已撤销）
     * @return 影响行数
     */
    @Update("UPDATE auth_platform SET status = #{status} WHERE user_id = #{userId} AND client_code = #{clientCode}")
    int updateStatus(@Param("userId") long userId, @Param("clientCode") String clientCode, @Param("status") int status);
    @Select("SELECT * FROM auth_platform WHERE user_id=#{userId} ORDER BY authorized_at DESC")
    java.util.List<AuthPlatform> listOwned(long userId);
}
