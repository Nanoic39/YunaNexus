package cc.nanoic.yunanexus.audit;

import cc.nanoic.yunanexus.audit.config.AuditProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 审计中心启动类（roadmap 阶段 8）。<p>
 *
 * 审计日志（{@code audit_log}）按操作者 {@code user_id % 逻辑分片数} 路由到物理分库，
 * 表内按 {@code create_time} 月度 RANGE 分区；各服务通过上报接口投递日志，经
 * RocketMQ 异步批量落库，支持按用户 / 时间 / 服务分页查询，超期分区滚动归档冷备。
 *
 * @author yunanexus
 */
@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@MapperScan("cc.nanoic.yunanexus.audit.mapper")
@EnableConfigurationProperties(AuditProperties.class)
@EnableScheduling
public class AuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }
}
