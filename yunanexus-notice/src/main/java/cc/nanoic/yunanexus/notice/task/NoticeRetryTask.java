package cc.nanoic.yunanexus.notice.task;

import cc.nanoic.yunanexus.notice.service.NoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知重试调度。
 *
 * <p>没有它，"退避重试"只是一句注释：失败记录躺在库里等人工处理。
 *
 * @author yunanexus
 */
@Slf4j
@Component
public class NoticeRetryTask {

    private final NoticeService noticeService;

    public NoticeRetryTask(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** 每分钟扫描一次到期重试（默认） */
    @Scheduled(cron = "${yunanexus.notice.retry.cron:0 */1 * * * ?}")
    public void retryDue() {
        try {
            int handled = noticeService.retryDue();
            if (handled > 0) {
                log.info("通知重试调度处理 {} 条", handled);
            }
        } catch (RuntimeException error) {
            log.warn("通知重试调度异常: {}", error.toString());
        }
    }
}
