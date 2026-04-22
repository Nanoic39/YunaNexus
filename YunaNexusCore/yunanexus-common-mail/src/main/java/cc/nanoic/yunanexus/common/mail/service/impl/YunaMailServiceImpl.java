package cc.nanoic.yunanexus.common.mail.service.impl;

import cc.nanoic.yunanexus.common.mail.config.MailTemplateProperties;
import cc.nanoic.yunanexus.common.mail.model.MailTemplate;
import cc.nanoic.yunanexus.common.mail.service.YunaMailService;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.Map;

public class YunaMailServiceImpl implements YunaMailService {
    private final JavaMailSender mailSender;
    private final MailTemplateProperties mailTemplateProperties;
    private final Map<String, MailTemplate> builtIn = new HashMap<>();

    public YunaMailServiceImpl(JavaMailSender mailSender, MailTemplateProperties mailTemplateProperties) {
        this.mailSender = mailSender;
        this.mailTemplateProperties = mailTemplateProperties;

        // TODO: 验证码通知的默认样式(如果Yaml文件中未定义)
        builtIn.put("VERIFICATION", new MailTemplate("验证码通知", ""));
    }
}
