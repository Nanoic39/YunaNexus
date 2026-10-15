package cc.nanoic.yunanexus.auth.mapper;

import cc.nanoic.yunanexus.auth.entity.AuthUnlockAppeal;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户解锁申诉 Mapper。<p>
 *
 * 记录用户提交的解锁申诉，支持管理后台审核（通过 / 拒绝）。项目未配置分页拦截器，
 * 分页查询以 LIMIT / OFFSET 手写实现。
 *
 * @author yunanexus
 */
@Mapper
public interface AuthUnlockAppealMapper extends BaseMapper<AuthUnlockAppeal> {

    /**
     * 查询某账号是否存在待处理（status=0）的申诉，用于防重复提交。
     *
     * @param username 账号
     * @return 待处理申诉条数
     */
    @Select("SELECT COUNT(*) FROM auth_unlock_appeal WHERE username = #{username} AND status = 0")
    int countPendingByUsername(@Param("username") String username);

    /**
     * 分页查询申诉列表（管理后台）。
     *
     * @param status 处理状态过滤，null 表示不过滤
     * @param offset 偏移量
     * @param limit  每页大小
     * @return 申诉列表
     */
    @Select("<script>"
            + "SELECT * FROM auth_unlock_appeal"
            + "<where><if test='status != null'> status = #{status}</if></where>"
            + " ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AuthUnlockAppeal> selectPage(@Param("status") Integer status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * 统计申诉总数（管理后台分页）。
     *
     * @param status 处理状态过滤，null 表示不过滤
     * @return 记录数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM auth_unlock_appeal"
            + "<where><if test='status != null'> status = #{status}</if></where>"
            + "</script>")
    long countByStatus(@Param("status") Integer status);

    /**
     * 审核申诉（仅待处理记录可被处理，避免并发重复审核）。<p>
     *
     * {@code WHERE status = 0} 保证幂等：已被审核的记录不会被覆盖。
     *
     * @param id          申诉 ID
     * @param status      目标状态（1-已通过 2-已拒绝）
     * @param auditor     处理人
     * @param auditRemark 处理意见
     * @param auditTime   处理时间
     * @return 影响行数（0 表示记录不存在或已被处理）
     */
    @Update("UPDATE auth_unlock_appeal SET status = #{status}, auditor = #{auditor}, "
            + "audit_remark = #{auditRemark}, audit_time = #{auditTime}, update_time = #{auditTime} "
            + "WHERE id = #{id} AND status = 0")
    int audit(@Param("id") long id,
              @Param("status") int status,
              @Param("auditor") String auditor,
              @Param("auditRemark") String auditRemark,
              @Param("auditTime") LocalDateTime auditTime);
}
