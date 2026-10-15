package cc.nanoic.yunanexus.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录 / 刷新成功后的令牌响应（对外暴露结构）。<p>
 *
 * 只暴露对外信息，内部 ID（userId）、会话 ID、jti 一律不下发（见 standards.md 安全规范）。
 *
 * @author yunanexus
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** access token 有效期（秒） */
    private long expiresIn;

    /** 外部 ID（对外唯一标识，永不暴露内部 ID） */
    private String externalId;
}
