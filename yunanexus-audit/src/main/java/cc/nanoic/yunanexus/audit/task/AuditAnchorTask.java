package cc.nanoic.yunanexus.audit.task;

import cc.nanoic.yunanexus.audit.chain.AuditAnchorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 外部锚定的定时入口（默认每小时第 5 分钟）。<p>
 *
 * 频率取舍：太密会让锚定文件膨胀且无意义（链头没变时不需要重复写），
 * 太稀疏则"被重写窗口"变长。一小时是"能接受的暴露窗口"与"文件体积"的折中，
 * 可通过 {@code yunanexus.audit.anchor.cron} 调整。
 *
 * @author yunanexus
 */
@Slf4j
@Component
public class AuditAnchorTask {

    private final AuditAnchorService anchorService;
    private final boolean enabled;

    public AuditAnchorTask(AuditAnchorService anchorService,
                           @Value("${yunanexus.audit.anchor.enabled:true}") boolean enabled) {
        this.anchorService = anchorService;
        this.enabled = enabled;
    }

    /** 定时锚定 */
    @Scheduled(cron = "${yunanexus.audit.anchor.cron:0 5 * * * ?}")
    public void scheduledAnchor() {
        if (!enabled) {
            return;
        }
        try {
            anchorService.anchorNow();
        } catch (RuntimeException error) {
            log.error("审计锚定任务异常", error);
        }
    }
}
