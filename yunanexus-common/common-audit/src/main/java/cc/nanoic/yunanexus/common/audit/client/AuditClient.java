package cc.nanoic.yunanexus.common.audit.client;

import cc.nanoic.yunanexus.common.audit.event.AuditEvent;

/**
 * 审计事件上报通道。<p>
 *
 * 实现必须保证：<b>永不阻塞、永不抛异常到业务线程</b>。审计是旁观者，
 * 不能因为审计服务宕机把业务拖死。
 *
 * @author yunanexus
 */
public interface AuditClient {

    /**
     * 提交一条事件（异步）。
     *
     * @param event 事件
     */
    void submit(AuditEvent event);

    /**
     * 当前队列积压量，用于监控与排障。
     *
     * @return 积压条数
     */
    int backlog();

    /**
     * 累计丢弃条数（队列满或重试耗尽）。
     *
     * @return 丢弃条数
     */
    long dropped();
}
