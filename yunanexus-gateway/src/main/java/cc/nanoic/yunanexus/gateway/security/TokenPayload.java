package cc.nanoic.yunanexus.gateway.security;

import java.time.Instant;

/**
 * JWT 验签后的负载（网关侧仅取所需字段）。
 *
 * @param jti        令牌唯一 ID
 * @param externalId 外部 ID（sub）
 * @param clientCode 客户端标识
 * @param sid        会话 ID
 * @param expiresAt  过期时间
 * @author yunanexus
 */
public record TokenPayload(String jti, String externalId, String clientCode, String sid, Instant expiresAt) {
}
