package cc.nanoic.yunanexus.auth.security;

/**
 * 服务端认证身份（仅内部使用，禁止序列化到响应/日志）。
 *
 * @param externalId 外部 ID
 * @param clientCode 客户端标识
 * @param sid        会话 ID
 * @param userId     内部用户 ID
 * @author yunanexus
 */
public record AuthenticatedIdentity(
    String externalId,
    String clientCode,
    String sid,
    long userId
) {
}
