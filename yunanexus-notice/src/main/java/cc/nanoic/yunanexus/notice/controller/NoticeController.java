package cc.nanoic.yunanexus.notice.controller;

import cc.nanoic.yunanexus.common.core.model.PageResult;
import cc.nanoic.yunanexus.common.core.model.Result;
import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import cc.nanoic.yunanexus.notice.domain.NoticeRecordView;
import cc.nanoic.yunanexus.notice.domain.NoticeSendRequest;
import cc.nanoic.yunanexus.notice.service.NoticeService;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通知发送与查询接口（roadmap 7.6 ~ 7.8）。
 *
 * <p>发送经 RocketMQ 延迟消息异步投递；站内信收件箱 / 已读操作以网关下发的 {@code X-User-Id} 作为接收方。
 *
 * @author yunanexus
 */
@RestController
@RequestMapping("/api/v1/notice")
public class NoticeController {

    /** 网关下发的用户 ID 请求头（与 GatewayProperties.userIdHeader 一致） */
    private static final String USER_ID_HEADER = "X-User-Id";

    @Resource private NoticeService noticeService;

    /** 发送通知（支持多接收方 / 多通道）。 */
    @PostMapping("/send")
    public Result<List<NoticeRecordView>> send(@Valid @RequestBody NoticeSendRequest request) {
        return Result.success(noticeService.send(request));
    }

    /** 分页查询当前用户站内信收件箱。 */
    @GetMapping("/inbox")
    public Result<PageResult<NoticeRecordView>> inbox(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(
                noticeService.inbox(String.valueOf(requireUserId(userId)), page, size, status));
    }

    @GetMapping("/inbox/summary")
    public Result<cc.nanoic.yunanexus.notice.domain.NoticeInboxSummary> inboxSummary(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        return Result.success(noticeService.inboxSummary(String.valueOf(requireUserId(userId))));
    }

    @PostMapping("/read/all")
    public Result<java.util.Map<String, Integer>> markAllRead(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        return Result.success(
                java.util.Map.of(
                        "changed",
                        noticeService.markAllRead(String.valueOf(requireUserId(userId)))));
    }

    /** 查询当前用户可见的单条站内信，不依赖收件箱第一页。 */
    @GetMapping("/inbox/{noticeId}")
    public Result<NoticeRecordView> inboxRecord(
            @PathVariable("noticeId") long noticeId,
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        String receiver = String.valueOf(requireUserId(userId));
        NoticeRecordView record = noticeService.getRecord(noticeId);
        if (record == null
                || !receiver.equals(record.getReceiver())
                || !Integer.valueOf(1).equals(record.getChannel())
                || (!Integer.valueOf(1).equals(record.getStatus())
                        && !Integer.valueOf(3).equals(record.getStatus()))) {
            throw new BizException(ErrorCode.NOTICE_RECORD_NOT_FOUND, "通知不存在: " + noticeId);
        }
        return Result.success(record);
    }

    /** 标记当前用户某条站内信已读。 */
    @PostMapping("/read/{noticeId}")
    public Result<Void> markRead(
            @PathVariable("noticeId") long noticeId,
            @RequestHeader(value = USER_ID_HEADER, required = false) Long userId) {
        noticeService.markRead(noticeId, String.valueOf(requireUserId(userId)));
        return Result.success();
    }

    /** 按全局通知 ID 查询记录详情。 */
    @GetMapping("/record/{noticeId}")
    public Result<NoticeRecordView> record(@PathVariable("noticeId") long noticeId) {
        return Result.success(noticeService.getRecord(noticeId));
    }

    /** 分页查询通知记录（可按模板编码 / 接收方 / 状态过滤）。 */
    @GetMapping("/record/list")
    public Result<PageResult<NoticeRecordView>> records(
            @RequestParam(value = "templateCode", required = false) String templateCode,
            @RequestParam(value = "receiver", required = false) String receiver,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(
                noticeService.listRecords(templateCode, receiver, status, page, size));
    }

    private long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少有效的 " + USER_ID_HEADER + " 请求头");
        }
        return userId;
    }
}
