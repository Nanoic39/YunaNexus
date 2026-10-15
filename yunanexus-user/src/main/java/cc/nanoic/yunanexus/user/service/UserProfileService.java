package cc.nanoic.yunanexus.user.service;

import cc.nanoic.yunanexus.user.domain.CreateUserProfileRequest;
import cc.nanoic.yunanexus.user.domain.SelfUpdateProfileRequest;
import cc.nanoic.yunanexus.user.domain.UpdateUserProfileRequest;
import cc.nanoic.yunanexus.user.domain.UserProfileView;

/**
 * 用户资料服务（roadmap 5.5 / 5.6）。<p>
 *
 * 所有方法以 {@code userId} 为分库键，调用前由 {@code @ShardRoute} 完成分片路由。
 *
 * @author yunanexus
 */
public interface UserProfileService {

    /**
     * 创建用户资料（幂等）。<p>
     *
     * 以 {@code userId} 为幂等键：已存在则直接返回其分片编号，不报错，保证 outbox 重试安全。
     *
     * @param userId  全局用户 ID
     * @param request 创建请求
     * @return 逻辑分片编号
     */
    int createUserProfile(long userId, CreateUserProfileRequest request);

    /**
     * 按全局用户 ID 查询用户资料。
     *
     * @param userId 全局用户 ID
     * @return 用户资料，不存在返回 {@code null}
     */
    UserProfileView getByUserId(long userId);

    /**
     * 判断用户资料是否存在（幂等回查，ADR-021）。
     *
     * @param userId 全局用户 ID
     * @return true 表示已存在
     */
    boolean exists(long userId);

    /**
     * 更新用户资料（仅更新非 {@code null} 字段）。
     *
     * @param userId  全局用户 ID
     * @param request 更新请求
     */
    void updateProfile(long userId, UpdateUserProfileRequest request);

    /**
     * 用户自助更新资料（对外接口，只能改自己的资料，仅更新非 {@code null} 字段）。
     *
     * @param userId  全局用户 ID（取自网关下发的 {@code X-User-Id}）
     * @param request 自助更新请求
     */
    void updateSelfProfile(long userId, SelfUpdateProfileRequest request);
}
