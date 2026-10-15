package cc.nanoic.yunanexus.user.mapper;

import cc.nanoic.yunanexus.user.entity.UserBadge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户徽章 Mapper。<p>
 *
 * 所有操作均以 {@code user_id} 为条件，调用前须已由 {@code @ShardRoute} 设定分片路由。
 *
 * @author yunanexus
 */
public interface UserBadgeMapper extends BaseMapper<UserBadge> {
}
