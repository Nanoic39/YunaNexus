package cc.nanoic.yunanexus.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码签发结果。<p>
 *
 * {@code code} 仅本地开发调试时回显（{@code yunanexus.auth.captcha.dev-return-code=true}），
 * 生产环境恒为 null，通过图形验证码展示。
 *
 * @author yunanexus
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResult {

    /** 验证码 ID，登录时随验证码一并提交 */
    private String captchaId;

    /** 验证码（仅开发环境回显，生产恒为 null） */
    private String code;

    /** PNG 图形，生产不返回明文验证码。 */
    private String imageDataUrl;

    public CaptchaResult(String captchaId, String code) {
        this(captchaId, code, null);
    }
}
