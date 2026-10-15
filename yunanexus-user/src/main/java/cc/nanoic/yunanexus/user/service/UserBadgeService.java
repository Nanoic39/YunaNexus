package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.user.domain.UserBadgeGrantRequest;
import cc.nanoic.yunanexus.user.domain.UserBadgeView;

import java.util.List;

/**
 * 用户徽章服务（roadmap 5.9）。
 *
 * @author yunanexus
 */
public interface UserBadgeService {

    /**
     * 查询用户徽章。
     *
     * @param userId 全局用户 ID
     * @return 徽章集合（按授予时间倒序）
     */
    List<UserBadgeView> listBadges(long userId);

    /**
     * 授予用户徽章（系统 / 运营调用，幂等更新）。
     *
     * @param userId  目标用户 ID
     * @param request 授予请求
     */
    void grantBadge(long userId, UserBadgeGrantRequest request);
}
