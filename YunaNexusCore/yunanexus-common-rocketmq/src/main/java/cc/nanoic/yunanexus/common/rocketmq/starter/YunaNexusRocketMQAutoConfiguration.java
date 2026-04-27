package cc.nanoic.yunanexus.common.rocketmq.starter;

import cc.nanoic.yunanexus.common.rocketmq.config.RocketMQProperties;
import cc.nanoic.yunanexus.common.rocketmq.service.YunaRocketMQService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(RocketMQTemplate.class)
@EnableConfigurationProperties(RocketMQProperties.class)
@ConditionalOnProperty(prefix = "yunanexus.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YunaNexusRocketMQAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(YunaRocketMQService.class)
    public YunaRocketMQService yunaRocketMQService(RocketMQTemplate rocketMQTemplate, RocketMQProperties rocketMQProperties) {
        return (topic, tag, key, payload) -> {
            // 构建消息目标
            // 格式：topic:tag
            String destination = topic + ":" + tag;
            // 构建消息
            MessageBuilder<String> builder = MessageBuilder.withPayload(payload);
            if (StringUtils.hasText(key)) {
                builder.setHeader(RocketMQHeaders.KEYS, key);
            }
            Message<String> message = builder.build();
            // 发送消息
            try {
                rocketMQTemplate.syncSend(destination, message, rocketMQProperties.getSendTimeoutMs(), rocketMQProperties.getSendRetryTimes());
            } catch (Exception e) {
                // TODO: 后续需要对接YunaNexusMonitor系统
                log.error("RocketMQ发送失败, destination={}, key={}", destination, key, e);
                throw e;
            }
        };
    }

}
