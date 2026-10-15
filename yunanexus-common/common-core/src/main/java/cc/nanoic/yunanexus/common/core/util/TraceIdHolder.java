package cc.nanoic.yunanexus.common.core.util;

/**
 * 链路追踪 ID 上下文持有器。<p>
 *
 * 通过 {@link ThreadLocal} 在当前请求线程内传递 traceId，供 {@code Result} 生成响应时自动填充。<p>
 *
 * 设计约束：common-core 保持纯净，不依赖 Web / SLF4J 框架，因此 traceId 的跨层传递
 * 使用自维护的 ThreadLocal，而非 SLF4J 的 MDC。MDC 由 common-web 的 {@code TraceIdFilter}
 * 负责写入，仅供日志框架使用。
 *
 * @author yunanexus
 */
public final class TraceIdHolder {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceIdHolder() {
    }

    /**
     * 设置当前线程的 traceId
     *
     * @param traceId 链路追踪 ID
     */
    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    /**
     * 获取当前线程的 traceId，未设置时返回 null
     *
     * @return traceId 或 null
     */
    public static String get() {
        return TRACE_ID.get();
    }

    /**
     * 清除当前线程的 traceId，避免线程复用时的脏数据
     */
    public static void clear() {
        TRACE_ID.remove();
    }
}
