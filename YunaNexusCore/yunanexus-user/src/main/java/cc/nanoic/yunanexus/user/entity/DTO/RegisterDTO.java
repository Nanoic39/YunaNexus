package cc.nanoic.yunanexus.user.entity.DTO;

import cc.nanoic.yunanexus.common.security.annotation.RSADecryptField;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterDTO {
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空!")
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 密码(经过RSA公钥加密)
     */
    @RSADecryptField
    @NotBlank(message = "密码不能为空!")
    @NotNull(message = "密码不能为空!")
    private String password;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式有误!")
    @NotBlank(message = "邮箱不能为空!")
    @NotNull(message = "邮箱不能为空!")
    private String email;

    /**
     * 邮箱验证码
     */
    @NotBlank(message = "邮箱验证码不能为空!")
    @NotNull(message = "邮箱验证码不能为空!")
    private String VerifyCode;
}
