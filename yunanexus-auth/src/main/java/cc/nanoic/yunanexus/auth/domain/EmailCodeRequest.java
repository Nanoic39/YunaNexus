package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 保持仅传 email 的客户端兼容；显式空值与未知用途均拒绝。 */
@Data
public class EmailCodeRequest {
    private String email;

    @NotNull(message = "验证码用途不能为空")
    private EmailCodePurpose purpose = EmailCodePurpose.REGISTER;
}
