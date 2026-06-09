package cc.nanoic.yunanexus.common.mail.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MailTemplate {
    private static final Logger logger = LoggerFactory.getLogger(MailTemplate.class);

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    // 根据模板渲染调用层
    public String render(MailTemplateType type, Map<String, String> params) {
        String html = cache.computeIfAbsent(type.getTemplateName(), name -> {
            try {
                ClassPathResource resource = new ClassPathResource("mail/templates/" + name + ".html");
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.error("邮件模板加载失败: {}", name, e);
                return "<p>模板加载失败</p>";
            }
        });
        return render(html, params);
    }

    // 渲染实现
    private String render(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
