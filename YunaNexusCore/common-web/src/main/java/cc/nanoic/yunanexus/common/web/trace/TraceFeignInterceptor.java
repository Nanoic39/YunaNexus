package cc.nanoic.yunanexus.common.web.trace;

import cn.hutool.core.util.RandomUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;

public class TraceFeignInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceContext.getTraceId();
        String currentSpanId = TraceContext.getSpanId();
        if (traceId != null) {
            template.header("X-Trace-Id", traceId);
        }
        if (currentSpanId != null) {
            template.header("X-Parent-Span-Id", currentSpanId);
        }
        template.header("X-Span-Id", RandomUtil.randomString(16));
    }
}
