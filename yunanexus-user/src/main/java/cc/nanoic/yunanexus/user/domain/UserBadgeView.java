package cc.nanoic.yunanexus.user.domain;

import cc.nanoic.yunanexus.user.entity.UserBadge;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户徽章视图（{@code GET /api/v1/user/badges} 返回元素）。
 *
 * @author yunanexus
 */
@Data
public class UserBadgeView {

    private String badgeCode;
    private String badgeName;
    private Integer badgeLevel;
    private String description;
    private String icon;
    private LocalDateTime grantedTime;

    public static UserBadgeView from(UserBadge badge) {
        UserBadgeView view = new UserBadgeView();
        view.setBadgeCode(badge.getBadgeCode());
        view.setBadgeName(badge.getBadgeName());
        view.setBadgeLevel(badge.getBadgeLevel());
        view.setDescription(badge.getDescription());
        view.setIcon(badge.getIcon());
        view.setGrantedTime(badge.getGrantedTime());
        return view;
    }
}
