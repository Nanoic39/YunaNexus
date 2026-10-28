package cc.nanoic.yunanexus.notice.task;

import cc.nanoic.yunanexus.notice.config.NoticeProperties;
import cc.nanoic.yunanexus.notice.entity.NoticeRecord;
import cc.nanoic.yunanexus.notice.mapper.NoticeRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 死信告警（邮件）。
 *
 * <p>通知进入死信后只写日志等于没人知道——这正是"通知静默丢失"最典型的形态。
 * 这里定期把死信汇总成一封邮件发给运维，形成闭环。
 *
 * @author yunanexus
 */
@Slf4j
@Component
public class DeadLetterAlertTask {

    private final NoticeRecordMapper recordMapper;
    private final NoticeProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public DeadLetterAlertTask(NoticeRecordMapper recordMapper, NoticeProperties properties,
                               ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.recordMapper = recordMapper;
        this.properties = properties;
        this.mailSenderProvider = mailSenderProvider;
    }

    /** 每小时检查一次死信（默认） */
    @Scheduled(cron = "${yunanexus.notice.dead-letter.cron:0 5 * * * ?}")
    public void alertDeadLetters() {
        NoticeProperties.DeadLetter config = properties.getDeadLetter();
        if (!config.isEnabled()) {
            return;
        }
        List<NoticeRecord> dead = recordMapper.selectRecentDeadLetters(
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(config.getWindowMinutes()),
                config.getBatchSize());
        if (dead.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder("YunaNexus 通知死信告警\n\n");
        body.append("近 ").append(config.getWindowMinutes()).append(" 分钟新增死信 ")
                .append(dead.size()).append(" 条：\n\n");
        for (NoticeRecord record : dead) {
            body.append("- noticeId=").append(record.getNoticeId())
                    .append(" channel=").append(record.getChannel())
                    .append(" receiver=").append(record.getReceiver())
                    .append(" attempts=").append(record.getRetryCount())
                    .append(" reason=").append(record.getFailReason())
                    .append('\n');
        }
        String recipient = config.getAlertTo();
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || recipient == null || recipient.isBlank()) {
            // 邮件通道不可用时退化为 ERROR 日志，至少留在日志采集里
            log.error("通知死信告警（邮件通道不可用，降级为日志）：\n{}", body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient);
            String from = properties.getMail().getFrom();
            if (from != null && !from.isBlank()) {
                message.setFrom(from);
            }
            message.setSubject("YunaNexus 通知死信告警（" + dead.size() + " 条）");
            message.setText(body.toString());
            sender.send(message);
            log.info("死信告警已发送，收件人={} 条数={}", recipient, dead.size());
        } catch (RuntimeException error) {
            log.error("死信告警发送失败：{}", error.toString());
            log.error("死信明细：\n{}", body);
        }
    }
}
