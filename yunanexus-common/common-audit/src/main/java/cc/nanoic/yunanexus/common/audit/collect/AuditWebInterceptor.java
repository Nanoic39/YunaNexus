package cc.nanoic.yunanexus.common.audit.collect;

import cc.nanoic.yunanexus.common.audit.annotation.AuditAction;
import cc.nanoic.yunanexus.common.audit.client.AuditClient;
import cc.nanoic.yunanexus.common.audit.config.AuditClientProperties;
import cc.nanoic.yunanexus.common.audit.event.AuditEvent;
import cc.nanoic.yunanexus.common.audit.mask.SensitiveMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务语义审计拦截器（方案 B）。<p>
 *
 * 只处理打了 {@link AuditAction} 的方法：网关已经负责"全量请求兜底"（方案 A），
 * 这里只补充网关拿不到的业务语义——模块、动作、风险级别、目标对象、执行结果。
 * 两者靠 traceId 在审计服务侧合并，不会产生重复记录。
 *
 * <p>所有异常都在内部消化：审计采集失败绝不能影响业务请求的返回。
 *
 * @author yunanexus
 */
@Slf4j
public class AuditWebInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = "yunanexus.audit.startMillis";
    private static final String ATTR_MODULE = "yunanexus.audit.module";
    private static final String ATTR_ACTION = "yunanexus.audit.action";
    private static final String ATTR_RISK = "yunanexus.audit.risk";
    private static final String ATTR_CAPTURE_PARAMS = "yunanexus.audit.captureParams";
    private static final String ATTR_CAPTURE_RESULT = "yunanexus.audit.captureResult";

    /** 结果摘要最大长度 */
    private static final int MAX_RESULT = 512;
    /** 错误信息最大长度 */
    private static final int MAX_ERROR = 512;
    /** UA 最大长度 */
    private static final int MAX_UA = 512;

    private final AuditClient client;
    private final AuditClientProperties properties;
    private final String serviceName;

    public AuditWebInterceptor(AuditClient client, AuditClientProperties properties, String serviceName) {
        this.client = client;
        this.properties = properties;
        this.serviceName = serviceName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            request.setAttribute(ATTR_START, System.currentTimeMillis());
            AuditCollectContext.clear();
            AuditAction annotation = resolveAnnotation(handler);
            if (annotation == null) {
                return true;
            }
            request.setAttribute(ATTR_MODULE, annotation.module());
            request.setAttribute(ATTR_ACTION, annotation.action());
            request.setAttribute(ATTR_RISK, annotation.risk());
            request.setAttribute(ATTR_CAPTURE_PARAMS, annotation.captureParams());
            request.setAttribute(ATTR_CAPTURE_RESULT, annotation.captureResult());
        } catch (RuntimeException error) {
            log.debug("审计拦截器 preHandle 异常（已忽略）：{}", error.toString());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            String module = (String) request.getAttribute(ATTR_MODULE);
            if (module == null) {
                return;
            }
            AuditEvent event = new AuditEvent();
            event.setService(serviceName);
            event.setModule(module);
            event.setAction((String) request.getAttribute(ATTR_ACTION));
            event.setMethod(request.getMethod());
            event.setUri(truncate(request.getRequestURI(), 512));
            event.setUserId(resolveUserId(request));
            event.setUsername(request.getHeader(properties.getUsernameHeader()));
            event.setIp(resolveIp(request));
            event.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_UA));
            event.setTraceId(request.getHeader("X-Trace-Id"));
            event.setCreateTime(System.currentTimeMillis());
            event.setDurationMs(resolveDuration(request));

            Boolean captureParams = (Boolean) request.getAttribute(ATTR_CAPTURE_PARAMS);
            if (Boolean.TRUE.equals(captureParams)) {
                event.setParams(buildParams(request));
            }
            Boolean captureResult = (Boolean) request.getAttribute(ATTR_CAPTURE_RESULT);
            if (Boolean.TRUE.equals(captureResult)) {
                event.setResult(truncate(AuditCollectContext.result(), MAX_RESULT));
            }
            if (ex != null || response.getStatus() >= 400) {
                event.setStatus(0);
                event.setErrorMsg(truncate(ex != null ? ex.getMessage() : "HTTP " + response.getStatus(), MAX_ERROR));
            } else {
                event.setStatus(1);
            }
            // 注解声明了风险级别时一并带上，服务端据此直接分级告警
            String risk = (String) request.getAttribute(ATTR_RISK);
            if (risk != null && !risk.isBlank()) {
                event.setResult(joinRisk(risk, event.getResult()));
            }
            client.submit(event);
        } catch (RuntimeException error) {
            log.debug("审计拦截器 afterCompletion 异常（已忽略）：{}", error.toString());
        } finally {
            AuditCollectContext.clear();
        }
    }

    private String joinRisk(String risk, String result) {
        String marker = "risk=" + risk;
        return result == null || result.isBlank() ? marker : marker + " | " + result;
    }

    private AuditAction resolveAnnotation(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return null;
        }
        AuditAction onMethod = method.getMethodAnnotation(AuditAction.class);
        if (onMethod != null) {
            return onMethod;
        }
        return method.getBeanType().getAnnotation(AuditAction.class);
    }

    private Long resolveUserId(HttpServletRequest request) {
        String raw = request.getHeader(properties.getUserIdHeader());
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private Long resolveDuration(HttpServletRequest request) {
        Object start = request.getAttribute(ATTR_START);
        if (start instanceof Long value) {
            return Math.max(0L, System.currentTimeMillis() - value);
        }
        return null;
    }

    private String buildParams(HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>(AuditCollectContext.fields());
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                fields.putIfAbsent(key, values[0]);
            }
        });
        return SensitiveMasker.render(fields);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return truncate(comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim(), 64);
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return truncate(real.trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
