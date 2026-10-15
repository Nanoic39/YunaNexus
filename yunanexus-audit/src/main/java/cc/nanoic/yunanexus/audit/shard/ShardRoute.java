package cc.nanoic.yunanexus.audit.shard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分库路由标注。<p>
 *
 * 标注在按 {@code userId} 分库访问的方法上，由 {@code ShardRoutingAspect} 从指定参数位
 * 取出 userId 决策分片，写入 {@code ShardContextHolder}，方法执行完毕自动清理。
 * 被标注方法的参数须通过 Spring 代理调用（避免同类自调用失效）。
 *
 * @author yunanexus
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShardRoute {

    /** 分片键（userId）所在的方法参数下标（默认第 0 个） */
    int idArgIndex() default 0;
}
