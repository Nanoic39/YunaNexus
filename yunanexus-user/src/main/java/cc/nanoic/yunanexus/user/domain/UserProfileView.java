package cc.nanoic.yunanexus.user.domain;

import cc.nanoic.yunanexus.user.entity.SysUser;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户资料视图（内部接口返回体）。<p>
 *
 * 不含任何凭证信息；{@code externalId} 为对外标识，供认证中心/网关使用。
 *
 * @author yunanexus
 */
@Data
public class UserProfileView {

    private Long userId;
    private Integer shardIndex;
    private String externalId;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String mobileCountry;
    private String mobile;
    private Integer status;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime createTime;
    @cc.nanoic.yunanexus.common.web.time.UtcDateTime
    private LocalDateTime updateTime;

    public static UserProfileView from(SysUser user) {
        UserProfileView view = new UserProfileView();
        view.setUserId(user.getUserId());
        view.setShardIndex(user.getShardIndex());
        view.setExternalId(user.getExternalId());
        view.setUsername(user.getUsername());
        view.setNickname(user.getNickname());
        view.setAvatar(user.getAvatar());
        view.setEmail(user.getEmail());
        view.setMobileCountry(user.getMobileCountry());
        view.setMobile(user.getMobile());
        view.setStatus(user.getStatus());
        view.setCreateTime(user.getCreateTime());
        view.setUpdateTime(user.getUpdateTime());
        return view;
    }
}
