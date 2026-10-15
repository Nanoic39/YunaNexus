package cc.nanoic.yunanexus.user.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户资料请求（内部接口）。<p>
 *
 * 仅更新非 {@code null} 字段（部分更新）。
 *
 * @author yunanexus
 */
@Data
public class UpdateUserProfileRequest {

    /** 全局用户 ID */
    @NotNull(message = "userId 不能为空")
    @PositiveOrZero(message = "userId 不能为负数")
    private Long userId;

    /** 昵称 */
    @Size(max = 64, message = "nickname 长度不能超过 64")
    private String nickname;

    /** 头像 URL */
    @Size(max = 512, message = "avatar 长度不能超过 512")
    private String avatar;

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
