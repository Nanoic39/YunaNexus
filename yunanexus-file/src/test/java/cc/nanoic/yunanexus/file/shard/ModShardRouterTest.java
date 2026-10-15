package cc.nanoic.yunanexus.file.shard;

import cc.nanoic.yunanexus.file.config.FileProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ModShardRouter} 单元测试。
 *
 * @author yunanexus
 */
class ModShardRouterTest {

    private ModShardRouter router(int logicalCount) {
        FileProperties properties = new FileProperties();
        properties.getShard().setLogicalCount(logicalCount);
        return new ModShardRouter(properties);
    }

    @Test
    void routeIsDeterministicModulo() {
        ModShardRouter router = router(16);
        assertEquals(0, router.route(0));
        assertEquals(1, router.route(1));
        assertEquals(15, router.route(15));
        assertEquals(0, router.route(16));
        assertEquals(7, router.route(1000007));
    }

    @Test
    void routeStaysWithinLogicalCount() {
        ModShardRouter router = router(4);
        for (long fileId = 0; fileId < 100; fileId++) {
            assertEquals(fileId % 4, router.route(fileId));
        }
    }

    @Test
    void negativeFileIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> router(16).route(-1));
    }

    @Test
    void logicalCountOutOfRangeRejected() {
        assertThrows(IllegalStateException.class, () -> router(0));
        assertThrows(IllegalStateException.class, () -> router(17));
    }
}
