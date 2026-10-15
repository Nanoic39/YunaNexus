package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.CaptchaResult;

/**
 * 验证码服务。<p>
 *
 * 登录失败达到阈值后要求验证码。验证码随机生成、有效期短（默认 5 分钟）、一次性消费。
 * 生产环境通过短信/邮件通道下发，本地开发可配置回显（见 {@code AuthProperties}）。
 *
 * @author yunanexus
 */
public interface CaptchaService {

    /**
     * 签发验证码。
     *
     * @param username 账号（用于限制验证码发送频率）
     * @return 验证码签发结果（含 captchaId，开发环境含 code）
     */
    CaptchaResult issue(String username);

    /**
     * 校验验证码。
     *
     * @param captchaId 验证码 ID
     * @param code      用户提交的验证码
     * @return true 校验通过；false 验证码不存在 / 已消费 / 错误
     */
    boolean verify(String captchaId, String code);
}
