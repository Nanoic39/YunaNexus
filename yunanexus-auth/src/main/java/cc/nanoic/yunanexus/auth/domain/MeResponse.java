package cc.nanoic.yunanexus.auth.domain;

/**
 * 当前身份响应（仅外部字段，不含内部 userId）。<p>
 *
 * {@code nickname} / {@code avatar} / {@code username} 由认证中心按内部 userId 调用用户中心
 * 资料接口聚合；用户中心不可用时降级为 {@code null}，不影响身份获取。
 *
 * @param externalId 外部 ID
 * @param clientCode 客户端标识
 * @param username   账号（可能为 {@code null}）
 * @param nickname   昵称（可能为 {@code null}）
 * @param avatar     头像 URL（可能为 {@code null}）
 * @author yunanexus
 */
public record MeResponse(
    String externalId,
    String clientCode,
    String username,
    String nickname,
    String avatar
) {
}
