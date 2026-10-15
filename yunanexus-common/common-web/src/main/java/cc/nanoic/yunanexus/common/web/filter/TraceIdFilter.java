package cc.nanoic.yunanexus.common.web.filter;

import cc.nanoic.yunanexus.common.core.util.TraceIdHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪 ID 过滤器。<p>
 *
 * 作为请求入口，生成或透传 traceId：<br>
 * 1. 从请求头 {@code X-Trace-Id} 读取，无则生成 UUID（去连字符）；<br>
 * 2. 写入 {@link TraceIdHolder}（供 Result 填充响应）与 MDC（供日志框架）；<br>
 * 3. 响应头回写 {@code X-Trace-Id}，便于前端定位问题；<br>
 * 4. 请求结束清理 ThreadLocal 与 MDC，避免线程复用脏数据。<p>
 *
 * 由业务模块通过 {@code scanBasePackages = "cc.nanoic.yunanexus"} 扫描注册。
 *
 * @author yunanexus
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 链路追踪 ID 请求头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC 中的 key，与日志框架 pattern 的 {@code %X{traceId}} 对应 */
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        TraceIdHolder.set(traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            TraceIdHolder.clear();
        }
    }
}
