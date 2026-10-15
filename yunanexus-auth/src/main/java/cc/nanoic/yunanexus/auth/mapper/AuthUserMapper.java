package cc.nanoic.yunanexus.auth.mapper;

import cc.nanoic.yunanexus.auth.entity.AuthUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证用户凭证 Mapper。<p>
 *
 * 提供账号唯一标识（username / email / mobile）检索，以及登录失败计数、
 * 锁定状态、最后登录信息的原子性更新。所有跨列一致性的写操作均通过单条 SQL
 * 完成，避免「读-改-写」竞态（与号段领取乐观锁同思路）。项目未配置分页拦截器，
 * 管理后台分页查询以 LIMIT / OFFSET 手写实现。
 *
 * @author yunanexus
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {

    /**
     * 按账号检索。
     *
     * @param username 账号
     * @return 用户凭证，不存在返回 null
     */
    @Select("SELECT * FROM auth_user WHERE username = #{username}")
    AuthUser selectByUsername(@Param("username") String username);

    /**
     * 按全局用户 ID 检索（管理后台）。
     *
     * @param userId 全局用户 ID
     * @return 用户凭证，不存在返回 null
     */
    @Select("SELECT * FROM auth_user WHERE user_id = #{userId}")
    AuthUser selectByUserId(@Param("userId") long userId);

    /**
     * 按邮箱检索（邮箱为可选登录凭证）。
     *
     * @param email 邮箱
     * @return 用户凭证，不存在返回 null
     */
    @Select("SELECT * FROM auth_user WHERE email = #{email}")
    AuthUser selectByEmail(@Param("email") String email);

    /**
     * 按手机号检索（国家号 + 手机号联合唯一）。
     *
     * @param mobileCountry 国家号（如 +86）
     * @param mobile        手机号
     * @return 用户凭证，不存在返回 null
     */
    @Select("SELECT * FROM auth_user WHERE mobile_country = #{mobileCountry} AND mobile = #{mobile}")
    AuthUser selectByMobile(@Param("mobileCountry") String mobileCountry, @Param("mobile") String mobile);

    /**
     * 登录失败：原子递增连续失败计数并记录失败时间。<p>
     *
     * 采用 {@code fail_count = fail_count + 1} 原子自增，避免并发登录尝试下计数丢失。
     *
     * @param username 账号
     * @return 影响行数
     */
    @Update("UPDATE auth_user SET fail_count = fail_count + 1, last_fail_time = #{now} "
            + "WHERE username = #{username}")
    int incrementFailCount(@Param("username") String username, @Param("now") LocalDateTime now);

    /**
     * 登录成功：清零失败计数并记录最后登录时间与 IP。<p>
     *
     * 失败计数清零与登录成功信息写入在同一事务中原子完成，避免成功登录后残留失败计数。
     *
     * @param username 账号
     * @param now      登录时间
     * @param ip       登录 IP
     * @return 影响行数
     */
    @Update("UPDATE auth_user SET fail_count = 0, last_login_time = #{now}, last_login_ip = #{ip} "
            + "WHERE username = #{username}")
    int resetFailCountOnLoginSuccess(@Param("username") String username,
                                     @Param("now") LocalDateTime now,
                                     @Param("ip") String ip);

    /**
     * 更新账号状态与锁定信息（锁定 / 解锁 / 禁用共用）。<p>
     *
     * @param userId      全局用户 ID
     * @param status      目标状态（1-正常 0-禁用 2-锁定）
     * @param lockedUntil 锁定截止时间（非锁定场景传 null）
     * @param lockReason  锁定原因（非锁定场景传 null）
     * @return 影响行数
     */
    @Update("UPDATE auth_user SET status = #{status}, locked_until = #{lockedUntil}, "
            + "lock_reason = #{lockReason} WHERE user_id = #{userId}")
    int updateStatusAndLock(@Param("userId") long userId,
                            @Param("status") int status,
                            @Param("lockedUntil") LocalDateTime lockedUntil,
                            @Param("lockReason") String lockReason);

    /**
     * 分页查询用户列表（管理后台）。<p>
     *
     * keyword 同时匹配 username / email / mobile 的模糊包含。
     *
     * @param keyword 关键字过滤，空表示不过滤
     * @param status  账号状态过滤，null 表示不过滤
     * @param offset  偏移量
     * @param limit   每页大小
     * @return 用户列表
     */
    @Select("<script>"
            + "SELECT * FROM auth_user"
            + "<where>"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (username LIKE CONCAT('%', #{keyword}, '%')"
            + " OR email LIKE CONCAT('%', #{keyword}, '%')"
            + " OR mobile LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "</where>"
            + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AuthUser> selectPage(@Param("keyword") String keyword,
                              @Param("status") Integer status,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /**
     * 统计用户总数（管理后台分页），过滤条件与 {@link #selectPage} 一致。
     *
     * @param keyword 关键字过滤，空表示不过滤
     * @param status  账号状态过滤，null 表示不过滤
     * @return 记录数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM auth_user"
            + "<where>"
            + "<if test='status != null'> AND status = #{status}</if>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND (username LIKE CONCAT('%', #{keyword}, '%')"
            + " OR email LIKE CONCAT('%', #{keyword}, '%')"
            + " OR mobile LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "</where>"
            + "</script>")
    long countByCondition(@Param("keyword") String keyword, @Param("status") Integer status);
}
