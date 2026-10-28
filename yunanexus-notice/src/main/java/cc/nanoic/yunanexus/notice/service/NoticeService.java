package cc.nanoic.yunanexus.notice.service;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.notice.domain.NoticeRecordView;
import cc.nanoic.yunanexus.notice.domain.NoticeSendRequest;

import java.util.List;

/**
 * 通知发送与投递服务（roadmap 7.6 ~ 7.8）。
 *
 * <p>{@link #send(NoticeSendRequest)} 渲染模板、逐接收方领取通知 ID 落库后投递延迟消息； {@link #dispatch(long)} 由 MQ
 * 消费者回调，按通道执行真实投递并回写记录状态。
 *
 * @author yunanexus
 */
public interface NoticeService {

    /**
     * 发送通知：查模板 → 校验启用 → 渲染 → 逐接收方落库并投递延迟消息。
     *
     * @param request 发送请求
     * @return 本次发送生成的记录视图（每接收方一条）
     */
    List<NoticeRecordView> send(NoticeSendRequest request);

    /**
     * 派发一条待发送通知（MQ 消费者回调）：按记录通道执行投递并回写状态。
     *
     * @param recordId 通知记录主键（{@code notice_record.id}）
     */
    void dispatch(long recordId);

    /**
     * 扫描到期重试的通知并重新投递（由调度任务触发）。
     *
     * @return 处理条数
     */
    int retryDue();

    /**
     * 分页查询站内信收件箱（已发送 / 已读）。
     *
     * @param receiver 接收方（用户 ID 字符串）
     * @param page 页码（从 1 起）
     * @param size 每页大小（上限 100）
     * @return 站内信分页结果
     */
    PageResult<NoticeRecordView> inbox(String receiver, int page, int size);

    PageResult<NoticeRecordView> inbox(String receiver, int page, int size, Integer status);

    cc.nanoic.yunanexus.notice.domain.NoticeInboxSummary inboxSummary(String receiver);

    int markAllRead(String receiver);

    /**
     * 标记站内信已读。
     *
     * @param noticeId 全局通知 ID
     * @param receiver 接收方（用户 ID 字符串）
     */
    void markRead(long noticeId, String receiver);

    /**
     * 按全局通知 ID 查询记录。
     *
     * @param noticeId 全局通知 ID
     * @return 记录视图
     */
    NoticeRecordView getRecord(long noticeId);

    /**
     * 分页查询通知记录。
     *
     * @param templateCode 模板编码（可选）
     * @param receiver 接收方（可选）
     * @param status 状态（可选）
     * @param page 页码（从 1 起）
     * @param size 每页大小（上限 100）
     * @return 记录分页结果
     */
    PageResult<NoticeRecordView> listRecords(
            String templateCode, String receiver, Integer status, int page, int size);
}
