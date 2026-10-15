package cc.nanoic.yunanexus.analytics;

import cc.nanoic.yunanexus.analytics.config.AnalyticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 行为分析（埋点）服务启动类（roadmap 阶段 10）。<p>
 *
 * 接收前端 SDK 批量上报的事件（{@code POST /api/v1/track}），补全字段后投递 RocketMQ
 * topic {@code analytics-event}，由消费者批量写入 ClickHouse（详见
 * {@code docs/analytics.md}）；并以 {@code /api/v1/analytics/**} 提供漏斗 / 留存 /
 * 分群 / 画像查询。埋点端点按设计走独立域名、绕过网关，避免网关承载埋点解析压力。
 *
 * @author yunanexus
 */
@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@EnableConfigurationProperties(AnalyticsProperties.class)
public class AnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}
