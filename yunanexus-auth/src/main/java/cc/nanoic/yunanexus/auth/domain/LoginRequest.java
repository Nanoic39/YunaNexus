package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。<p>
 *
 * {@code username} 支持账号 / 邮箱 / 手机号三种凭证（见 api-contract.md 3.1）。
 * 连续登录失败达到阈值后需携带验证码（{@code captchaId} + {@code captcha}）。
 *
 * @author yunanexus
 */
@Data
public class LoginRequest {

    /** 登录凭证：username / email / mobile */
    @NotBlank(message = "账号不能为空")
    private String username;

    /** 明文密码（HTTPS 传输） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 ID（失败 ≥ 阈值后必填） */
    private String captchaId;

    /** 验证码（失败 ≥ 阈值后必填） */
    private String captcha;

    /** SSO 客户端标识 */
    @NotBlank(message = "客户端标识不能为空")
    private String clientCode;
}
