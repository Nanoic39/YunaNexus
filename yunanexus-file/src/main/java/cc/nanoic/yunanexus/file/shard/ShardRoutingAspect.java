package cc.nanoic.yunanexus.file.shard;

import cc.nanoic.yunanexus.file.config.ShardContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 分库路由拦截器（AOP + ThreadLocal）。<p>
 *
 * 拦截 {@link ShardRoute} 标注的方法，从指定参数取 fileId 决策分片并写入
 * {@link ShardContextHolder}；无论方法正常返回或抛异常，均在 finally 中清理，防止
 * 线程复用导致路由串库。
 *
 * @author yunanexus
 */
@Aspect
@Component
public class ShardRoutingAspect {

    private final ShardRouter shardRouter;

    public ShardRoutingAspect(ShardRouter shardRouter) {
        this.shardRouter = shardRouter;
    }

    @Around("@annotation(shardRoute)")
    public Object routeByFileId(ProceedingJoinPoint joinPoint, ShardRoute shardRoute) throws Throwable {
        Object[] args = joinPoint.getArgs();
        int index = shardRoute.idArgIndex();
        if (index < 0 || index >= args.length || !(args[index] instanceof Number fileId)) {
            throw new IllegalStateException("@ShardRoute 未取到 fileId 参数: " + joinPoint.getSignature());
        }
        ShardContextHolder.set(shardRouter.route(fileId.longValue()));
        try {
            return joinPoint.proceed();
        } finally {
            ShardContextHolder.clear();
        }
    }
}
