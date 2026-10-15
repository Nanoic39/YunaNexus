package cc.nanoic.yunanexus.common.datascope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link DataScopeContext} 单元测试。
 *
 * @author yunanexus
 */
class DataScopeContextTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void 设置读取与清理() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope(false, List.of(1L), 100L);
        DataScopeContext.set(scope);
        assertSame(scope, DataScopeContext.get());

        DataScopeContext.clear();
        assertNull(DataScopeContext.get());
    }

    @Test
    void execute执行后恢复原上下文() {
        DataScopeContext.Scope outer = new DataScopeContext.Scope(false, List.of(1L), null);
        DataScopeContext.Scope inner = DataScopeContext.Scope.unrestricted();
        DataScopeContext.set(outer);

        String result = DataScopeContext.execute(inner, () -> {
            assertSame(inner, DataScopeContext.get());
            return "ok";
        });

        assertEquals("ok", result);
        assertSame(outer, DataScopeContext.get());
    }

    @Test
    void execute异常时也恢复并清理() {
        DataScopeContext.Scope inner = DataScopeContext.Scope.unrestricted();

        try {
            DataScopeContext.execute(inner, () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ignored) {
            // 预期
        }

        assertNull(DataScopeContext.get());
    }
}
