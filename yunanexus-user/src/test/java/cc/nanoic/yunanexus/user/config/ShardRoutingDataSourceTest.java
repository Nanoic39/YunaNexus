package cc.nanoic.yunanexus.user.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ShardRoutingDataSource} 与 {@link ShardContextHolder} 单元测试。
 *
 * @author yunanexus
 */
class ShardRoutingDataSourceTest {

    @Test
    void mapsLogicalShardToPhysicalByModulo() {
        ShardRoutingDataSource dataSource = new ShardRoutingDataSource(2);
        ShardContextHolder.set(0);
        assertEquals(0, dataSource.determineCurrentLookupKey());
        ShardContextHolder.set(1);
        assertEquals(1, dataSource.determineCurrentLookupKey());
        ShardContextHolder.set(2);
        assertEquals(0, dataSource.determineCurrentLookupKey());
        ShardContextHolder.set(15);
        assertEquals(1, dataSource.determineCurrentLookupKey());
        ShardContextHolder.clear();
    }

    @Test
    void missingContextFailsFast() {
        ShardRoutingDataSource dataSource = new ShardRoutingDataSource(2);
        ShardContextHolder.clear();
        assertThrows(IllegalStateException.class, dataSource::determineCurrentLookupKey);
    }

    @Test
    void invalidPhysicalCountRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ShardRoutingDataSource(0));
    }

    @Test
    void contextHolderSetGetClear() {
        ShardContextHolder.set(9);
        assertEquals(9, ShardContextHolder.get());
        ShardContextHolder.clear();
        assertNull(ShardContextHolder.get());
    }
}
