package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.user.domain.UserBadgeGrantRequest;
import cc.nanoic.yunanexus.user.domain.UserBadgeView;
import cc.nanoic.yunanexus.user.entity.UserBadge;
import cc.nanoic.yunanexus.user.mapper.UserBadgeMapper;
import cc.nanoic.yunanexus.user.service.UserBadgeService;
import cc.nanoic.yunanexus.user.shard.ShardRoute;
import cc.nanoic.yunanexus.user.shard.ShardRouter;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户徽章服务实现（roadmap 5.9）。
 *
 * @author yunanexus
 */
@Service
public class UserBadgeServiceImpl implements UserBadgeService {

    private final UserBadgeMapper userBadgeMapper;
    private final ShardRouter shardRouter;

    public UserBadgeServiceImpl(UserBadgeMapper userBadgeMapper, ShardRouter shardRouter) {
        this.userBadgeMapper = userBadgeMapper;
        this.shardRouter = shardRouter;
    }

    @Override
    @ShardRoute
    public List<UserBadgeView> listBadges(long userId) {
        return userBadgeMapper.selectList(Wrappers.<UserBadge>lambdaQuery()
                        .eq(UserBadge::getUserId, userId)
                        .orderByDesc(UserBadge::getGrantedTime)
                        .orderByDesc(UserBadge::getId))
                .stream()
                .map(UserBadgeView::from)
                .toList();
    }

    @Override
    @ShardRoute
    public void grantBadge(long userId, UserBadgeGrantRequest request) {
        UserBadge existing = userBadgeMapper.selectOne(Wrappers.<UserBadge>lambdaQuery()
                .eq(UserBadge::getUserId, userId)
                .eq(UserBadge::getBadgeCode, request.getBadgeCode()));
        if (existing != null) {
            existing.setBadgeName(request.getBadgeName());
            existing.setBadgeLevel(resolveLevel(request));
            existing.setDescription(request.getDescription());
            existing.setIcon(request.getIcon());
            userBadgeMapper.updateById(existing);
            return;
        }
        UserBadge badge = new UserBadge();
        badge.setUserId(userId);
        badge.setShardIndex(shardRouter.route(userId));
        badge.setBadgeCode(request.getBadgeCode());
        badge.setBadgeName(request.getBadgeName());
        badge.setBadgeLevel(resolveLevel(request));
        badge.setDescription(request.getDescription());
        badge.setIcon(request.getIcon());
        badge.setGrantedTime(LocalDateTime.now());
        try { userBadgeMapper.insert(badge); }
        catch(org.springframework.dao.DuplicateKeyException e) {
            // Concurrent idempotent grants may race on user_id + badge_code.
            if(userBadgeMapper.selectOne(Wrappers.<UserBadge>lambdaQuery().eq(UserBadge::getUserId,userId).eq(UserBadge::getBadgeCode,request.getBadgeCode()))==null)throw e;
        }
    }

    private static int resolveLevel(UserBadgeGrantRequest request) {
        return request.getBadgeLevel() == null ? UserBadge.DEFAULT_LEVEL : request.getBadgeLevel();
    }
}
