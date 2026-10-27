package cc.nanoic.yunanexus.audit.service;

import cc.nanoic.yunanexus.audit.domain.AuditMessage;
import cc.nanoic.yunanexus.audit.domain.AuditQueryFilter;
import cc.nanoic.yunanexus.audit.domain.AuditReportRequest;
import cc.nanoic.yunanexus.audit.entity.AuditLog;
import cc.nanoic.yunanexus.common.core.model.PageResult;

import java.util.List;

/**
 * 审计日志采集与查询服务（roadmap 8.2 / 8.3）。<p>
 *
 * {@link #report(AuditReportRequest)} 由上报接口调用，校验后投递异步消息；
 * {@link #persist(AuditMessage)} 由 MQ 消费者回调，按 {@code userId} 路由分片落库；
 * {@link #queryLogs} 由查询接口调用，跨分片归并后分页返回。
 *
 * @author yunanexus
 */
public interface AuditService {

    /**
     * 上报一条审计日志（仅入队，不同步落库）。
     *
     * @param request 上报请求
     */
    void report(AuditReportRequest request);

    /**
     * 持久化一条审计日志（MQ 消费者回调）：按 {@code user_id} 路由分片后落库。
     *
     * @param message 审计消息
     */
    void persist(AuditMessage message);

    /**
     * 分页查询审计日志（roadmap 8.3）。<p>
     *
     * 指定 {@code userId} 时命中单一分片；否则跨全部分片归并后分页。
     *
     * @param userId    操作者用户 ID 过滤，null 表示不过滤（跨分片）
     * @param service   来源服务过滤，空表示不过滤
     * @param startTime 操作时间下界（epoch 毫秒，含），null 表示不限
     * @param endTime   操作时间上界（epoch 毫秒，含），null 表示不限
     * @param page      页码（从 1 起）
     * @param size      每页大小
     * @return 分页结果（按操作时间倒序）
     */
    /**
     * 校验审计链完整性。
     *
     * @param userId 指定用户则只校验其所在分片；为空则遍历全部物理库
     * @param fromId 起始 id（含），为空表示从头
     * @param toId   结束 id（含），为空表示到最新
     * @param limit  单分片最多校验条数
     * @return 校验报告
     */
    ChainReport verifyIntegrity(Long userId, Long fromId, Long toId, int limit);

    /**
     * 链校验报告。
     *
     * @param verified    实际校验条数
     * @param intact      是否全部完整
     * @param breaks      断裂明细（分片:记录id:原因），完整时为空
     */
    record ChainReport(int verified, boolean intact, List<String> breaks) {
    }

    /**
     * 多条件检索（分页）。
     *
     * @param filter 检索条件（内部会规范化）
     * @param page   页码（1 起）
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<AuditLog> searchLogs(AuditQueryFilter filter, int page, int size);

    /**
     * 导出检索结果为 CSV（上限见实现，防拖库）。
     *
     * @param filter 检索条件
     * @return UTF-8 带 BOM 的 CSV 字节
     */
    byte[] exportCsv(AuditQueryFilter filter);

    PageResult<AuditLog> queryLogs(Long userId, String service, Long startTime, Long endTime, int page, int size);
}
