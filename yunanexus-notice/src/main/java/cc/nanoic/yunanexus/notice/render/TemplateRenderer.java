package cc.nanoic.yunanexus.notice.render;

import cc.nanoic.yunanexus.common.web.constant.ErrorCode;
import cc.nanoic.yunanexus.common.web.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染器。<p>
 *
 * 将模板中的 {@code ${key}} 占位符替换为参数值；任一占位符缺参即抛
 * {@link ErrorCode#NOTICE_PARAM_MISSING}，避免发出缺字段的通知。
 *
 * @author yunanexus
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 渲染模板。
     *
     * @param template 模板文本（可为空，空则原样返回）
     * @param params   参数
     * @return 渲染结果
     */
    public String render(String template, Map<String, String> params) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = params == null ? null : params.get(key);
            if (value == null) {
                throw new BizException(ErrorCode.NOTICE_PARAM_MISSING, "缺少模板参数: " + key);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
