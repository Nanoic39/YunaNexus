package cc.nanoic.yunanexus.user.entity.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmailVerifySend {
    /**
     * 邮箱
     */
    @Email(message = "邮箱格式错误")
    @NotBlank(message = "邮箱不能为空")
    @NotNull(message = "邮箱不能为空")
    private String email;
}
