package cc.nanoic.yunanexus.user.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建用户资料请求（内部接口，由认证中心注册 outbox 补偿调用）。<p>
 *
 * {@code shardIndex} 由认证中心决策并随 outbox 下发；用户中心以自身路由策略复算并校验一致性。
 *
 * @author yunanexus
 */
@Data
public class CreateUserProfileRequest {

    /** 全局用户 ID */
    @NotNull(message = "userId 不能为空")
    @PositiveOrZero(message = "userId 不能为负数")
    private Long userId;

    /** 逻辑分片编号（认证中心决策，用于一致性校验） */
    private Integer shardIndex;

    /** 外部 ID */
    @NotBlank(message = "externalId 不能为空")
    @Size(max = 64, message = "externalId 长度不能超过 64")
    private String externalId;

    /** 账号 */
    @NotBlank(message = "username 不能为空")
    @Size(max = 64, message = "username 长度不能超过 64")
    private String username;

    /** 昵称 */
    @Size(max = 64, message = "nickname 长度不能超过 64")
    private String nickname;

    /** 邮箱 */
    @Size(max = 128, message = "email 长度不能超过 128")
    private String email;

    /** 手机号国家号 */
    @Size(max = 8, message = "mobileCountry 长度不能超过 8")
    private String mobileCountry;

    /** 手机号 */
    @Size(max = 32, message = "mobile 长度不能超过 32")
    private String mobile;
}
