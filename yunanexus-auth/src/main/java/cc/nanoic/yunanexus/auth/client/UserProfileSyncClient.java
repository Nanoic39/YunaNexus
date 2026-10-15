package cc.nanoic.yunanexus.auth.client;

import cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox;

/**
 * 用户资料同步客户端。<p>
 *
 * 认证中心注册成功后，通过本接口将用户资料同步到用户中心（{@code sys_user}）。
 * 实现见 {@link FeignUserProfileSyncClient}。
 *
 * @author yunanexus
 */
public interface UserProfileSyncClient {

    /**
     * 创建用户资料（幂等）。<p>
     *
     * 用户中心须以 {@code userId} 为幂等键：重复调用返回已存在的资料而不报错，
     * 以保证 outbox 重试安全（ADR-021）。
     *
     * @param outbox 注册 outbox 记录（含 userId / externalId / shardIndex / 联系方式）
     */
    void createUserProfile(AuthRegisterOutbox outbox);

    /**
     * 幂等回查：查询用户资料是否存在。
     *
     * @param userId 全局用户 ID
     * @return true 表示用户中心已存在该用户资料
     */
    boolean exists(long userId);

    /**
     * 查询用户资料（用于 {@code /api/v1/auth/me} 聚合昵称 / 头像）。
     *
     * @param userId 全局用户 ID
     * @return 用户资料，不存在返回 {@code null}
     */
    UserProfileView getProfile(long userId);
}
