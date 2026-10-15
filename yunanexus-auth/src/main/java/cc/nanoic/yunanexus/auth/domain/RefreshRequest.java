package cc.nanoic.yunanexus.auth.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求。
 *
 * @author yunanexus
 */
@Data
public class RefreshRequest {

    /** 刷新令牌 */
    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
