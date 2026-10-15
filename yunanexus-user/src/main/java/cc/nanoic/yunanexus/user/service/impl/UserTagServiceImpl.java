package cc.nanoic.yunanexus.user.service.impl;

import cc.nanoic.yunanexus.user.domain.UserTagView;
import cc.nanoic.yunanexus.user.entity.UserTag;
import cc.nanoic.yunanexus.user.mapper.UserTagMapper;
import cc.nanoic.yunanexus.user.service.UserTagService;
import cc.nanoic.yunanexus.user.shard.ShardRoute;
import cc.nanoic.yunanexus.user.shard.ShardRouter;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户标签服务实现（roadmap 5.9）。
 *
 * @author yunanexus
 */
@Service
public class UserTagServiceImpl implements UserTagService {

    private final UserTagMapper userTagMapper;
    private final ShardRouter shardRouter;

    public UserTagServiceImpl(UserTagMapper userTagMapper, ShardRouter shardRouter) {
        this.userTagMapper = userTagMapper;
        this.shardRouter = shardRouter;
    }

    @Override
    @ShardRoute
    public List<UserTagView> listTags(long userId) {
        return userTagMapper.selectList(Wrappers.<UserTag>lambdaQuery()
                        .eq(UserTag::getUserId, userId)
                        .orderByAsc(UserTag::getId))
                .stream()
                .map(UserTagView::from)
                .toList();
    }

    @Override
    @ShardRoute
    public void setTags(long userId, List<UserTagView> tags) {
        userTagMapper.delete(Wrappers.<UserTag>lambdaQuery().eq(UserTag::getUserId, userId));
        if (tags == null || tags.isEmpty()) {
            return;
        }
        int shardIndex = shardRouter.route(userId);
        // 按 tagCode 去重，避免请求内重复触发唯一键冲突
        Map<String, UserTagView> deduped = new LinkedHashMap<>();
        for (UserTagView tag : tags) {
            deduped.putIfAbsent(tag.getTagCode(), tag);
        }
        for (UserTagView item : deduped.values()) {
            UserTag tag = new UserTag();
            tag.setUserId(userId);
            tag.setShardIndex(shardIndex);
            tag.setTagCode(item.getTagCode());
            tag.setTagName(item.getTagName());
            tag.setColor(item.getColor());
            userTagMapper.insert(tag);
        }
    }
}
