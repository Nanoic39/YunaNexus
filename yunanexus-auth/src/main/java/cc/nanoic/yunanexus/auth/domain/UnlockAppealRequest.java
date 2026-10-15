package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账户解锁申诉请求。<p>
 *
 * 需验证密码以证明账号归属（见 api-contract.md 3.5）。
 *
 * @author yunanexus
 */
@Data
public class UnlockAppealRequest {

    /** 被锁定的账号 */
    @NotBlank(message = "账号不能为空")
    private String username;

    /** 明文密码，用于证明账号归属 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 申诉说明 */
    @Size(max = 500, message = "申诉说明不能超过 500 字")
    private String appealReason;

    /** 联系方式（邮箱/手机号） */
    @Size(max = 128, message = "联系方式不能超过 128 字")
    private String contact;
}
