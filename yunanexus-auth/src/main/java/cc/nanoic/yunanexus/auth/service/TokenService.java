package cc.nanoic.yunanexus.auth.service;

import java.time.Instant;

/**
 * JWT 令牌服务（kid 版本化，ADR-015）。<p>
 *
 * 负责 access token / refresh token 的签发与验证。access token 短效（30min）、
 * refresh token 长效（7d），均携带 {@code jti} 供黑名单吊销，签名密钥按 {@code kid}
 * 选择实现平滑轮换。令牌仅包含外部身份字段，不包含内部用户 ID。
 *
 * @author yunanexus
 */
public interface TokenService {

    /**
     * 签发访问令牌。
     *
     * @param externalId 外部 ID（仅非空约束，不重写外部 ID 算法）
     * @param clientCode 客户端标识
     * @param sid        会话 ID（登出时据此定位会话）
     * @param jti        令牌唯一 ID（会话持有，供吊销）
     * @return JWT 字符串
     */
    String issueAccessToken(String externalId, String clientCode, String sid, String jti);

    /**
     * 签发刷新令牌。
     *
     * @param externalId 外部 ID
     * @param clientCode 客户端标识
     * @param sid        会话 ID（刷新令牌与会话绑定）
     * @param jti        令牌唯一 ID
     * @return JWT 字符串
     */
    String issueRefreshToken(String externalId, String clientCode, String sid, String jti);

    /**
     * 一次性签发访问令牌与刷新令牌对，使用同一时钟瞬间计算过期时间。
     *
     * @param externalId 外部 ID
     * @param clientCode 客户端标识
     * @param sid        会话 ID
     * @param accessJti  访问令牌唯一 ID
     * @param refreshJti 刷新令牌唯一 ID
     * @return 令牌对（含真实过期时间）
     */
    TokenPair issueTokenPair(String externalId, String clientCode, String sid, String accessJti, String refreshJti);

    /**
     * 验证并解析访问令牌。
     *
     * @param token JWT 字符串
     * @return 令牌负载
     * @throws cc.nanoic.yunanexus.common.web.exception.BizException 令牌无效 / 过期 / 撤销
     */
    TokenPayload verifyAccessToken(String token);

    /**
     * 验证并解析刷新令牌（要求类型为 refresh）。
     *
     * @param token JWT 字符串
     * @return 令牌负载
     * @throws cc.nanoic.yunanexus.common.web.exception.BizException 令牌无效 / 过期 / 撤销
     */
    TokenPayload verifyRefreshToken(String token);

    /**
     * 令牌负载。仅包含外部身份字段，不包含内部用户 ID。
     */
    record TokenPayload(String jti, String externalId, String clientCode, String sid, Instant expiresAt) {
    }

    /**
     * 令牌对（含真实过期时间，来自签名时的时钟，不额外验签）。
     */
    record TokenPair(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {
    }
}
