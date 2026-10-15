package cc.nanoic.yunanexus.auth.client;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户中心用户资料视图（Feign 契约，对应 user 侧 {@code GET /internal/user/byUserId} 的 data）。<p>
 *
 * 认证中心仅用于幂等回查（判断资料是否已存在），不做业务改写。
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
