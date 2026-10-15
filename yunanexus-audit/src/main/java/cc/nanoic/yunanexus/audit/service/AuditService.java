package cc.nanoic.yunanexus.audit.service;

import cc.nanoic.yunanexus.audit.domain.AuditMessage;
import cc.nanoic.yunanexus.audit.domain.AuditReportRequest;
import cc.nanoic.yunanexus.audit.entity.AuditLog;
import cc.nanoic.yunanexus.common.core.model.PageResult;

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
    PageResult<AuditLog> queryLogs(Long userId, String service, Long startTime, Long endTime, int page, int size);
}
