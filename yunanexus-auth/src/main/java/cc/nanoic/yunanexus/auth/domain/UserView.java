package cc.nanoic.yunanexus.auth.domain;

import cc.nanoic.yunanexus.auth.entity.AuthUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证用户视图（管理后台）。<p>
 *
 * 由 {@link AuthUser} 转换而来，刻意不包含密码哈希等敏感字段。
 *
 * @author yunanexus
 */
@Data
public class UserView {

    private Long userId;
    private String externalId;
    private String username;
    private String email;
    private String mobileCountry;
    private String mobile;
    private Integer status;
    private LocalDateTime lockedUntil;
    private String lockReason;
    private Integer failCount;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 由实体转换为视图（剔除密码）。
     *
     * @param user 用户实体
     * @return 视图对象
     */
    public static UserView from(AuthUser user) {
        UserView view = new UserView();
        view.setUserId(user.getUserId());
        view.setExternalId(user.getExternalId());
        view.setUsername(user.getUsername());
        view.setEmail(user.getEmail());
        view.setMobileCountry(user.getMobileCountry());
        view.setMobile(user.getMobile());
        view.setStatus(user.getStatus());
        view.setLockedUntil(user.getLockedUntil());
        view.setLockReason(user.getLockReason());
        view.setFailCount(user.getFailCount());
        view.setLastLoginTime(user.getLastLoginTime());
        view.setLastLoginIp(user.getLastLoginIp());
        view.setCreateTime(user.getCreateTime());
        view.setUpdateTime(user.getUpdateTime());
        return view;
    }
}
