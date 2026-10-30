package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮箱验证码登录请求。
 *
 * @author yunanexus
 */
@Data
public class EmailCodeLoginRequest {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "邮箱验证码不能为空")
    private String emailCode;

    @NotBlank(message = "客户端标识不能为空")
    private String clientCode;
}
