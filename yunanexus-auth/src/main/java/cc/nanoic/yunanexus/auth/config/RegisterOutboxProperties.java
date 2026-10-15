package cc.nanoic.yunanexus.auth.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 注册 outbox 补偿配置（ADR-021）。<p>
 *
 * 前缀 {@code yunanexus.auth.outbox}。用户中心已接入，{@code enabled} 默认 true：
 * 后台补偿任务周期性将待同步记录投递到用户中心。下游不可用时置 false 可暂停投递
 * （outbox 记录仍随注册写入，durable 不丢失）。
 *
 * @author yunanexus
 */
@Data
@Validated
@ConfigurationProperties(prefix = "yunanexus.auth.outbox")
public class RegisterOutboxProperties {

    /** 是否启用后台补偿任务（默认开启；下游不可用时置 false 可暂停投递） */
    private boolean enabled = true;

    /** 每批扫描条数 */
    private int batchSize = 100;

    /** 最大重试次数，超过置 FAILED 并告警 */
    private int maxRetry = 8;

    /** 首次重试延迟（秒），后续按指数退避 */
    private long initialBackoffSeconds = 30;

    /** 退避上限（秒） */
    private long maxBackoffSeconds = 3600;

    /** 是否启用只读对账；独立于向用户中心投递的补偿开关。 */
    private boolean reconciliationEnabled = false;

    /** PENDING 从创建起达到此秒数即告警（默认一天）。 */
    @Min(1)
    private long reconciliationOverdueSeconds = 86400;
}
