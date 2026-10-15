package cc.nanoic.yunanexus.auth.mapper;

import cc.nanoic.yunanexus.auth.entity.AuthRegisterOutbox;
import cc.nanoic.yunanexus.auth.domain.RegisterOutboxReconciliation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 注册本地消息表 Mapper（outbox，ADR-021）。<p>
 *
 * 提供待同步记录扫描、同步结果标记与对账统计。所有状态更新均为原子 UPDATE，
 * 避免后台任务与业务写入的竞态。
 *
 * @author yunanexus
 */
@Mapper
public interface AuthRegisterOutboxMapper extends BaseMapper<AuthRegisterOutbox> {

    /**
     * 扫描待同步记录（按 next_retry_time 到期，限制批量大小）。<p>
     *
     * 多实例并行扫描可能取到同一批记录，但下游 {@link cc.nanoic.yunanexus.auth.client.UserProfileSyncClient}
     * 以 {@code userId} 为幂等键（ADR-021），重复投递安全；状态更新由 {@code status = 0} 条件
     * 保护，重复标记不产生副作用。若需严格单实例消费，可在此基础上加分布式锁。
     *
     * @param now   当前时间
     * @param limit 批量大小
     * @return 待同步记录
     */
    @Select("SELECT * FROM auth_register_outbox WHERE status = 0 AND next_retry_time <= #{now} "
            + "ORDER BY next_retry_time ASC LIMIT #{limit}")
    List<AuthRegisterOutbox> selectPending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 标记同步成功。
     *
     * @param userId 全局用户 ID
     * @param now    同步时间
     * @return 影响行数
     */
    @Update("UPDATE auth_register_outbox SET status = 1, synced_time = #{now}, last_error = NULL "
            + "WHERE user_id = #{userId} AND status = 0")
    int markDone(@Param("userId") long userId, @Param("now") LocalDateTime now);

    /**
     * 记录一次同步失败并安排下次重试（指数退避由调用方计算）。
     *
     * @param userId        全局用户 ID
     * @param retryCount    重试后次数
     * @param nextRetryTime 下次重试时间
     * @param lastError     失败原因（脱敏）
     * @return 影响行数
     */
    @Update("UPDATE auth_register_outbox SET retry_count = #{retryCount}, next_retry_time = #{nextRetryTime}, "
            + "last_error = #{lastError} WHERE user_id = #{userId} AND status = 0")
    int markRetry(@Param("userId") long userId,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("lastError") String lastError);

    /**
     * 标记同步失败（超重试上限，需人工介入）。
     *
     * @param userId    全局用户 ID
     * @param retryCount 最终重试次数
     * @param lastError  失败原因（脱敏）
     * @return 影响行数
     */
    @Update("UPDATE auth_register_outbox SET status = 2, retry_count = #{retryCount}, last_error = #{lastError} "
            + "WHERE user_id = #{userId} AND status = 0")
    int markFailed(@Param("userId") long userId,
                   @Param("retryCount") int retryCount,
                   @Param("lastError") String lastError);

    /**
     * 按状态统计记录数（对账 / 监控指标）。
     *
     * @param status 同步状态
     * @return 记录数
     */
    @Select("SELECT COUNT(*) FROM auth_register_outbox WHERE status = #{status}")
    long countByStatus(@Param("status") int status);

    /**
     * 同一条只读查询统计积压及认证库内差异，避免逐条加载用户资料。
     * 超时按创建时间计算，身份匹配只比较不可变的 external_id / shard_index。
     *
     * @param cutoff 创建时间小于等于此值的 PENDING 记录视为超时（与写入端使用同一时钟）
     * @return 对账数量；不代表用户中心真实资料已完成跨服务核验
     */
    @Select("""
            SELECT
              (SELECT COUNT(*) FROM auth_register_outbox WHERE status = 0) AS pending,
              (SELECT COUNT(*) FROM auth_register_outbox WHERE status = 2) AS failed,
              (SELECT COUNT(*) FROM auth_register_outbox
                WHERE status = 0 AND create_time <= #{cutoff}) AS overdue,
              (SELECT COUNT(*) FROM auth_user u
                WHERE NOT EXISTS (SELECT 1 FROM auth_register_outbox o
                                  WHERE o.user_id = u.user_id)) AS missing,
              (SELECT COUNT(*) FROM auth_register_outbox o
                WHERE NOT EXISTS (SELECT 1 FROM auth_user u
                                  WHERE u.user_id = o.user_id)) AS orphan,
              (SELECT COUNT(*) FROM auth_register_outbox o
                JOIN auth_user u ON u.user_id = o.user_id
                WHERE BINARY o.external_id <> BINARY u.external_id
                   OR o.shard_index <> u.shard_index) AS mismatch
            """)
    @Options(timeout = 30)
    RegisterOutboxReconciliation reconcile(@Param("cutoff") LocalDateTime cutoff);
}
