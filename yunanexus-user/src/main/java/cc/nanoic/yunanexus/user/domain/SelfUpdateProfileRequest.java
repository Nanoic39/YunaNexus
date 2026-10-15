package cc.nanoic.yunanexus.user.domain;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户自助修改资料请求（对外接口 {@code PUT /api/v1/user/profile}）。<p>
 *
 * 与内部 {@link UpdateUserProfileRequest} 的区别：不含 {@code userId}——用户身份由网关下发的
 * {@code X-User-Id} 决定，客户端无法指定他人。仅更新非 {@code null} 字段（部分更新）。
 *
 * @author yunanexus
 */
@Data
public class SelfUpdateProfileRequest {

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
