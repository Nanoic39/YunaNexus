package cc.nanoic.yunanexus.auth.client;

import lombok.Data;

/**
 * 用户中心「创建用户资料」请求体（Feign 契约，对应 user 侧 {@code POST /internal/user/create}）。<p>
 *
 * {@code shardIndex} 由认证中心在注册时决策并随 outbox 下发；用户中心复算校验一致性。
 *
 * @author yunanexus
 */
@Data
public class UserProfileCreateRequest {

    /** 全局用户 ID */
    private Long userId;

    /** 逻辑分片编号 */
    private Integer shardIndex;

    /** 外部 ID */
    private String externalId;

    /** 账号 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号国家号 */
    private String mobileCountry;

    /** 手机号 */
    private String mobile;
}
