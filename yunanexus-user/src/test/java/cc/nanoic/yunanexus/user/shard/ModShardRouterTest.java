package cc.nanoic.yunanexus.user.shard;

import cc.nanoic.yunanexus.user.config.UserProperties;
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
        UserProperties properties = new UserProperties();
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
        for (long userId = 0; userId < 100; userId++) {
            int shard = router.route(userId);
            assertEquals(userId % 4, shard);
        }
    }

    @Test
    void negativeUserIdRejected() {
        ModShardRouter router = router(16);
        assertThrows(IllegalArgumentException.class, () -> router.route(-1));
    }

    @Test
    void logicalCountOutOfRangeRejected() {
        assertThrows(IllegalStateException.class, () -> router(0));
        assertThrows(IllegalStateException.class, () -> router(17));
    }
}
