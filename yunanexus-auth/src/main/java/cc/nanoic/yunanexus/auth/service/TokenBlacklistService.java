package cc.nanoic.yunanexus.auth.service;

import java.time.Instant;

/**
 * JWT 撤销服务（Redis，ADR-003）。<p>
 *
 * 已撤销令牌按 sid 维度写入 ZSET（member=jti, score=绝对过期毫秒），
 * 通过 Redis TIME 权威判定过期，不使用固定 TTL 猜测。
 *
 * @author yunanexus
 */
public interface TokenBlacklistService {

    /**
     * 判断令牌是否已被撤销。
     *
     * @param sid 会话 ID
     * @param jti 令牌唯一 ID
     * @return true 表示已撤销
     */
    boolean isRevoked(String sid, String jti);

    /**
     * 撤销令牌（写入 ZSET，score = 绝对过期毫秒）。
     *
     * @param sid       会话 ID
     * @param jti       令牌唯一 ID
     * @param expiresAt 令牌过期时间（绝对时间）
     */
    void revoke(String sid, String jti, Instant expiresAt);
}
