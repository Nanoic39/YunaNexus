package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。<p>
 *
 * 用户名、密码、邮箱及注册用途的邮箱验证码必填。手机号能力仍由独立开关控制。
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

    /** 邮箱（服务层与发码共用格式检查，落库前统一去空格、转小写） */
    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 注册用途的一次性邮箱验证码 */
    @NotBlank(message = "邮箱验证码不能为空")
    @Pattern(regexp = "^[0-9]{6}$", message = "邮箱验证码需为 6 位数字")
    private String emailCode;

    /** 手机号国家号（手机号存在时默认 +86） */
    private String mobileCountry;

    /** 手机号（可选） */
    private String mobile;
}
