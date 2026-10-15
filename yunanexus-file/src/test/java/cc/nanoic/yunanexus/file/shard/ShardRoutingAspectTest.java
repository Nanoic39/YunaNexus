package cc.nanoic.yunanexus.file.shard;

import cc.nanoic.yunanexus.file.config.ShardContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ShardRoutingAspect} 单元测试。
 *
 * @author yunanexus
 */
class ShardRoutingAspectTest {

    /** 承载 {@link ShardRoute} 的样例方法 */
    static class Sample {
        @ShardRoute
        void byFirstArg(long fileId) {
        }

        @ShardRoute(idArgIndex = 1)
        void bySecondArg(String prefix, long fileId) {
        }
    }

    @AfterEach
    void tearDown() {
        ShardContextHolder.clear();
    }

    private ShardRoute annotationOf(String method, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method m = Sample.class.getDeclaredMethod(method, parameterTypes);
        return m.getAnnotation(ShardRoute.class);
    }

    @Test
    void setsContextFromFileIdAndClearsAfter() throws Throwable {
        ShardRouter router = mock(ShardRouter.class);
        when(router.route(1001L)).thenReturn(5);
        ShardRoutingAspect aspect = new ShardRoutingAspect(router);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{1001L});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertEquals(5, ShardContextHolder.get());
            return "ok";
        });

        Object result = aspect.routeByFileId(joinPoint, annotationOf("byFirstArg", long.class));

        assertEquals("ok", result);
        assertNull(ShardContextHolder.get(), "方法结束后必须清理路由上下文");
    }

    @Test
    void clearsContextWhenTargetThrows() throws Throwable {
        ShardRouter router = mock(ShardRouter.class);
        when(router.route(7L)).thenReturn(3);
        ShardRoutingAspect aspect = new ShardRoutingAspect(router);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{7L});
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class,
                () -> aspect.routeByFileId(joinPoint, annotationOf("byFirstArg", long.class)));
        assertNull(ShardContextHolder.get(), "异常路径也必须清理路由上下文");
    }

    @Test
    void readsFileIdFromConfiguredArgIndex() throws Throwable {
        ShardRouter router = mock(ShardRouter.class);
        when(router.route(42L)).thenReturn(11);
        ShardRoutingAspect aspect = new ShardRoutingAspect(router);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"prefix", 42L});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertEquals(11, ShardContextHolder.get());
            return null;
        });

        aspect.routeByFileId(joinPoint, annotationOf("bySecondArg", String.class, long.class));
    }

    @Test
    void rejectsWhenFileIdArgMissingOrNotNumber() throws Throwable {
        ShardRoutingAspect aspect = new ShardRoutingAspect(mock(ShardRouter.class));

        ProceedingJoinPoint empty = mock(ProceedingJoinPoint.class);
        when(empty.getArgs()).thenReturn(new Object[]{});
        assertThrows(IllegalStateException.class,
                () -> aspect.routeByFileId(empty, annotationOf("byFirstArg", long.class)));

        ProceedingJoinPoint wrongType = mock(ProceedingJoinPoint.class);
        when(wrongType.getArgs()).thenReturn(new Object[]{"not-a-number"});
        assertThrows(IllegalStateException.class,
                () -> aspect.routeByFileId(wrongType, annotationOf("byFirstArg", long.class)));
    }
}
