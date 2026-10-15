package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。<p>
 *
 * 用户名、密码必填；邮箱、手机号可选但至少提供一种联系方式（供找回 / 申诉反馈）。
 *
 * @author yunanexus
 */
@Data
public class RegisterRequest {

    /** 账号（唯一） */
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 64, message = "账号长度需在 4~64 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "账号仅支持字母、数字、下划线、点、中划线")
    private String username;

    /** 明文密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需在 8~64 之间")
    private String password;

    /** 邮箱（可选） */
    private String email;

    /** 手机号国家号（手机号存在时默认 +86） */
    private String mobileCountry;

    /** 手机号（可选） */
    private String mobile;
}
