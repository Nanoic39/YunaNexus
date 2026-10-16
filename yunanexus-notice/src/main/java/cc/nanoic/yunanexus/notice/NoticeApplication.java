package cc.nanoic.yunanexus.notice;

import cc.nanoic.yunanexus.notice.config.NoticeProperties;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 通知中心启动类（roadmap 阶段 7）。
 *
 * <p>模板管理 + 多通道通知发送：站内信 / 邮件由本服务真实投递，短信仅预留通道； 发送经 RocketMQ 延迟消息异步解耦（原生 {@code rocketmq-client}，自建
 * Producer/Consumer）。
 *
 * @author yunanexus
 */
@SpringBootApplication(scanBasePackages = "cc.nanoic.yunanexus")
@MapperScan("cc.nanoic.yunanexus.notice.mapper")
@EnableConfigurationProperties(NoticeProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
@EnableFeignClients(basePackages = "cc.nanoic.yunanexus.notice.client")
public class NoticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoticeApplication.class, args);
    }
}
