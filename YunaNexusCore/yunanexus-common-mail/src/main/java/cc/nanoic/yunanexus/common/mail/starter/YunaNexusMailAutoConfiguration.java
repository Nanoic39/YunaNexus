package cc.nanoic.yunanexus.common.mail.starter;

import cc.nanoic.yunanexus.common.mail.config.MailTemplateProperties;
import cc.nanoic.yunanexus.common.mail.service.YunaMailService;
import cc.nanoic.yunanexus.common.mail.service.impl.YunaMailServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

@AutoConfiguration
@ConditionalOnClass(JavaMailSender.class)
@EnableConfigurationProperties(MailTemplateProperties.class)
public class YunaNexusMailAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    @ConditionalOnProperty(prefix = "yunanexus.mail", name = "host")
    public JavaMailSender javaMailSender(MailTemplateProperties p) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(p.getHost());
        if (p.getPort() != null) sender.setPort(p.getPort());
        sender.setUsername(p.getUsername());
        sender.setPassword(p.getPassword());
        sender.setProtocol(p.getProtocol());
        sender.getJavaMailProperties().putAll(p.getProperties());
        return sender;
    }

    @Bean
    @ConditionalOnMissingBean(YunaMailService.class)
    @ConditionalOnProperty(prefix = "yunanexus.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
    public YunaMailService yunaMailService(JavaMailSender mailSender, MailTemplateProperties properties) {
        if (!StringUtils.hasText(properties.getFromAddress())) {
            throw new IllegalStateException("yunanexus.mail.from-address 未配置");
        }
        return new YunaMailServiceImpl(mailSender, properties);
    }
}
