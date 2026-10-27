package cc.nanoic.yunanexus.audit.mapper;

import cc.nanoic.yunanexus.audit.entity.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志 Mapper。<p>
 *
 * 分库写入前须由 {@code ShardRoutingAspect} / 显式 {@code ShardContextHolder} 提供
 * 路由上下文；跨分片查询在 service 层逐物理库 set 上下文后调用。
 *
 * @author yunanexus
 */
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 按条件分页查询审计日志（在当前分片上下文的物理库内执行）。<p>
     *
     * 仅接收当前分片窗口，{@code offset} 由 service 层在跨分片归并后施加，
     * 故此处只做 {@code LIMIT}，不接收 {@code OFFSET}。
     *
     * @param userId    操作者用户 ID 过滤，null 表示不过滤
     * @param service   来源服务过滤，空表示不过滤
     * @param startTime 操作时间下界（含），null 表示不限
     * @param endTime   操作时间上界（含），null 表示不限
     * @param limit     本物理库最多返回条数
     * @return 按操作时间倒序的日志列表
     */
    @Select("<script>"
            + "SELECT * FROM audit_log"
            + "<where>"
            + "<if test='userId != null'> AND user_id = #{userId}</if>"
            + "<if test='service != null and service != \"\"'> AND service = #{service}</if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime}</if>"
            + "<if test='endTime != null'> AND create_time &lt;= #{endTime}</if>"
            + "</where>"
            + " ORDER BY create_time DESC, id DESC LIMIT #{limit}"
            + "</script>")
    List<AuditLog> selectByCondition(@Param("userId") Long userId,
                                     @Param("service") String service,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("limit") int limit);

    /**
     * 统计审计日志总数（在当前分片上下文的物理库内执行），条件与
     * {@link #selectByCondition} 一致。
     *
     * @param userId    操作者用户 ID 过滤，null 表示不过滤
     * @param service   来源服务过滤，空表示不过滤
     * @param startTime 操作时间下界（含），null 表示不限
     * @param endTime   操作时间上界（含），null 表示不限
     * @return 记录数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM audit_log"
            + "<where>"
            + "<if test='userId != null'> AND user_id = #{userId}</if>"
            + "<if test='service != null and service != \"\"'> AND service = #{service}</if>"
            + "<if test='startTime != null'> AND create_time &gt;= #{startTime}</if>"
            + "<if test='endTime != null'> AND create_time &lt;= #{endTime}</if>"
            + "</where>"
            + "</script>")
    /**
     * 取该分片当前的链头摘要（最后一条记录的 record_hash）。
     *
     * @param shardIndex 逻辑分片编号
     * @return 链头摘要；分片内尚无记录时返回 {@code null}
     */
    @Select("SELECT record_hash FROM audit_log WHERE shard_index = #{shardIndex} "
            + "ORDER BY id DESC LIMIT 1")
    String selectHeadHash(@Param("shardIndex") int shardIndex);

    /**
     * 按 id 升序取一段记录用于链校验。
     *
     * @param shardIndex 逻辑分片编号
     * @param fromId     起始 id（含）
     * @param toId       结束 id（含）
     * @param limit      最多返回条数
     * @return 记录列表
     */
    @Select("SELECT * FROM audit_log WHERE shard_index = #{shardIndex} "
            + "AND id &gt;= #{fromId} AND id &lt;= #{toId} ORDER BY id ASC LIMIT #{limit}")
    List<AuditLog> selectChainSegment(@Param("shardIndex") int shardIndex,
                                      @Param("fromId") long fromId,
                                      @Param("toId") long toId,
                                      @Param("limit") int limit);

    long countByCondition(@Param("userId") Long userId,
                          @Param("service") String service,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);
}
