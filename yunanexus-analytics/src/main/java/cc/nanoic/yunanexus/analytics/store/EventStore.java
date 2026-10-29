package cc.nanoic.yunanexus.analytics.store;

import cc.nanoic.yunanexus.analytics.domain.TrackEvent;

import java.time.LocalDateTime;

/**
 * 埋点事件存储抽象。
 *
 * <p>当前实现是 MySQL（立即可用、零新增运维）；接口刻意与查询能力解耦，
 * 将来换成 ClickHouse 只需新增一个实现并改配置，业务代码不动。
 *
 * @author yunanexus
 */
public interface EventStore {

    /**
     * 写入一条事件。重复 eventId 应被幂等忽略。
     *
     * @param event 事件
     * @param ip    客户端 IP
     * @return true 表示已落库（重复上报视为已存在，同样返回 true）
     */
    boolean append(TrackEvent event, String ip);

    /**
     * 删除早于指定时间的事件（保留策略）。
     *
     * @param before 截止时间
     * @param limit  单批上限
     * @return 删除行数
     */
    int purgeBefore(LocalDateTime before, int limit);

    /**
     * 统计某时间范围内的事件数（用于健康检查与验证）。
     *
     * @param from 起始
     * @param to   结束
     * @return 条数
     */
    long countBetween(LocalDateTime from, LocalDateTime to);
}
