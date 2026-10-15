package cc.nanoic.yunanexus.auth.service;

import cc.nanoic.yunanexus.auth.domain.AppealView;
import cc.nanoic.yunanexus.auth.domain.UserView;
import cc.nanoic.yunanexus.common.core.model.PageResult;

/**
 * 认证中心管理后台服务（roadmap 3.9）。<p>
 *
 * 提供解锁申诉审核与用户管理能力，供运维 / 客服后台调用。所有端点经
 * {@code X-Admin-Token} 鉴权后进入，方法本身不再做终端用户身份校验。
 *
 * @author yunanexus
 */
public interface AdminService {

    /**
     * 分页查询申诉列表。
     *
     * @param status 处理状态过滤（0-待处理 1-已通过 2-已拒绝），null 表示全部
     * @param page   页码（从 1 起）
     * @param size   每页大小
     * @return 分页申诉视图
     */
    PageResult<AppealView> listAppeals(Integer status, int page, int size);

    /**
     * 查询申诉详情。
     *
     * @param id 申诉 ID
     * @return 申诉视图
     */
    AppealView getAppeal(long id);

    /**
     * 通过申诉：标记已通过并解锁关联账号（清除锁定与失败计数）。
     *
     * @param id      申诉 ID
     * @param auditor 处理人（可为空，缺省记为 admin）
     * @param remark  处理意见
     */
    void approveAppeal(long id, String auditor, String remark);

    /**
     * 驳回申诉。
     *
     * @param id      申诉 ID
     * @param auditor 处理人（可为空，缺省记为 admin）
     * @param remark  处理意见
     */
    void rejectAppeal(long id, String auditor, String remark);

    /**
     * 分页查询用户列表。
     *
     * @param keyword 关键字（匹配账号 / 邮箱 / 手机号），空表示不过滤
     * @param status  账号状态过滤（1-正常 0-禁用 2-锁定），null 表示全部
     * @param page    页码（从 1 起）
     * @param size    每页大小
     * @return 分页用户视图
     */
    PageResult<UserView> listUsers(String keyword, Integer status, int page, int size);

    /**
     * 查询用户详情。
     *
     * @param userId 全局用户 ID
     * @return 用户视图
     */
    UserView getUser(long userId);

    /**
     * 启用账号（置为正常并清除锁定与失败计数）。
     *
     * @param userId 全局用户 ID
     */
    void enableUser(long userId);

    /**
     * 禁用账号（活动令牌因实时状态校验随即失效）。
     *
     * @param userId 全局用户 ID
     */
    void disableUser(long userId);

    /**
     * 解锁账号（清除锁定与失败计数）。
     *
     * @param userId 全局用户 ID
     */
    void unlockUser(long userId);

    /**
     * 踢出账号全部会话（撤销纪元 + 清理会话）。
     *
     * @param userId 全局用户 ID
     */
    void kickUser(long userId);
}
