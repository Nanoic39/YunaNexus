package cc.nanoic.yunanexus.auth.service.impl;

import cc.nanoic.yunanexus.auth.config.ShardProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModShardAllocator} 单元测试。<p>
 *
 * 覆盖：取模分布、边界（0 / 分片数-1）、分片数非法拒绝、负 userId 拒绝。
 *
 * @author yunanexus
 */
class ModShardAllocatorTest {

    private ModShardAllocator allocator(int shardCount) {
        ShardProperties props = new ShardProperties();
        props.setShardCount(shardCount);
        return new ModShardAllocator(props);
    }

    @Test
    void 取模分配正确() {
        ModShardAllocator a = allocator(16);
        assertEquals(0, a.allocate(16));
        assertEquals(1, a.allocate(17));
        assertEquals(15, a.allocate(31));
        assertEquals(0, a.allocate(0));
    }

    @Test
    void 连续ID均匀轮询() {
        int shardCount = 16;
        ModShardAllocator a = allocator(shardCount);
        int[] counts = new int[shardCount];
        for (long id = 1; id <= 1600; id++) {
            counts[a.allocate(id)]++;
        }
        for (int c : counts) {
            assertEquals(100, c, "16 分片下 1600 个 ID 应均匀分布");
        }
    }

    @Test
    void 分片数越界应拒绝() {
        assertThrows(IllegalStateException.class, () -> allocator(0));
        assertThrows(IllegalStateException.class, () -> allocator(17));
    }

    @Test
    void 单分片时恒为0() {
        ModShardAllocator a = allocator(1);
        for (long id = 0; id < 10; id++) {
            assertEquals(0, a.allocate(id));
        }
    }

    @Test
    void 负userId应拒绝() {
        ModShardAllocator a = allocator(16);
        assertThrows(IllegalArgumentException.class, () -> a.allocate(-1));
    }

    @Test
    void 结果始终在范围内() {
        ModShardAllocator a = allocator(16);
        for (long id = 0; id < 1000; id++) {
            int s = a.allocate(id);
            assertTrue(s >= 0 && s <= 15);
        }
    }
}
