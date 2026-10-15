package cc.nanoic.yunanexus.common.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SecureExternalIdGenerator} 单元测试。<p>
 *
 * 覆盖：编解码往返、篡改检测、边界值、非确定性输出、多版本密钥轮换。
 *
 * @author yunanexus
 */
class SecureExternalIdGeneratorTest {

    private static final int FIXED_STR_LEN = 29;

    /** 版本 0 密钥 */
    private static final String AES_KEY_V0 = "MDEyMzQ1Njc4OWFiY2RlZg=="; // 16 字节
    private static final String HMAC_KEY_V0 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // 32 字节

    /** 版本 1 密钥（与 v0 不同，用于轮换测试） */
    private static final String AES_KEY_V1 = "ZmVkY2JhOTg3NjU0MzIxMA=="; // 16 字节
    private static final String HMAC_KEY_V1 = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA="; // 32 字节

    private static SecureExternalIdGenerator newGenerator(int version) {
        List<SecureExternalIdGenerator.KeyVersion> keys = new java.util.ArrayList<>();
        keys.add(new SecureExternalIdGenerator.KeyVersion(0, AES_KEY_V0, HMAC_KEY_V0));
        // 仅当版本号不同时才追加第二个密钥，避免版本号 0 重复
        if (version != 0) {
            keys.add(new SecureExternalIdGenerator.KeyVersion(version, AES_KEY_V1, HMAC_KEY_V1));
        }
        return new SecureExternalIdGenerator(keys);
    }

    @Test
    void 正常编解码往返() {
        SecureExternalIdGenerator g = newGenerator(0);
        String externalId = g.encode(12345L, 3);
        assertEquals(FIXED_STR_LEN, externalId.length());

        SecureExternalIdGenerator.DecodeResult r = g.decode(externalId);
        assertEquals(12345L, r.internalId());
        assertEquals(3, r.shardIndex());
    }

    @Test
    void 篡改任一字符应失败() {
        SecureExternalIdGenerator g = newGenerator(0);
        String externalId = g.encode(12345L, 3);
        // 篡改中间一个字符
        char[] chars = externalId.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(SecurityException.class, () -> g.decode(tampered));
    }

    @Test
    void 内部ID边界值() {
        SecureExternalIdGenerator g = newGenerator(0);

        long id0 = g.decode(g.encode(0L, 0)).internalId();
        assertEquals(0L, id0);

        long maxId = (1L << 56) - 1;
        long decodedMax = g.decode(g.encode(maxId, 15)).internalId();
        assertEquals(maxId, decodedMax);
    }

    @Test
    void 分片编号边界值() {
        SecureExternalIdGenerator g = newGenerator(0);

        assertEquals(0, g.decode(g.encode(1L, 0)).shardIndex());
        assertEquals(15, g.decode(g.encode(1L, 15)).shardIndex());
    }

    @Test
    void 非法长度输入应失败() {
        SecureExternalIdGenerator g = newGenerator(0);
        assertThrows(IllegalArgumentException.class, () -> g.decode("short"));
        assertThrows(IllegalArgumentException.class, () -> g.decode(null));
    }

    @Test
    void 越界参数应失败() {
        SecureExternalIdGenerator g = newGenerator(0);
        assertThrows(IllegalArgumentException.class, () -> g.encode(1L, -1));
        assertThrows(IllegalArgumentException.class, () -> g.encode(1L, 16));
        assertThrows(IllegalArgumentException.class, () -> g.encode(-1L, 0));
        assertThrows(IllegalArgumentException.class, () -> g.encode(1L << 56, 0));
    }

    @Test
    void 同一内部ID多次编码结果不同但可解码回同一ID() {
        SecureExternalIdGenerator g = newGenerator(0);
        String id1 = g.encode(9999L, 5);
        String id2 = g.encode(9999L, 5);

        assertNotEquals(id1, id2, "随机 IV 应导致每次编码结果不同");

        assertEquals(9999L, g.decode(id1).internalId());
        assertEquals(9999L, g.decode(id2).internalId());
        assertEquals(5, g.decode(id1).shardIndex());
        assertEquals(5, g.decode(id2).shardIndex());
    }

    @Test
    void 多版本密钥轮换() {
        // v0 生成器：最新版本 0，用它编码得到 v0 密文
        SecureExternalIdGenerator g0 = new SecureExternalIdGenerator(List.of(
                new SecureExternalIdGenerator.KeyVersion(0, AES_KEY_V0, HMAC_KEY_V0)
        ));
        String oldId = g0.encode(777L, 2);

        // v1 生成器：同时持有 v0 与 v1 密钥，latest = 1
        SecureExternalIdGenerator g1 = newGenerator(1);
        String newId = g1.encode(777L, 2);

        // 旧 ID 能被新生成器（含 v0 密钥）解码
        assertEquals(777L, g1.decode(oldId).internalId());
        // 新 ID 也能被解码（用 v1 密钥）
        assertEquals(777L, g1.decode(newId).internalId());

        // 仅持有 v1 密钥的生成器无法解码 v0 密文
        SecureExternalIdGenerator onlyV1 = new SecureExternalIdGenerator(List.of(
                new SecureExternalIdGenerator.KeyVersion(1, AES_KEY_V1, HMAC_KEY_V1)
        ));
        assertThrows(SecurityException.class, () -> onlyV1.decode(oldId));
    }

    @Test
    void 密钥版本号重复应拒绝() {
        assertThrows(IllegalArgumentException.class, () -> new SecureExternalIdGenerator(List.of(
                new SecureExternalIdGenerator.KeyVersion(0, AES_KEY_V0, HMAC_KEY_V0),
                new SecureExternalIdGenerator.KeyVersion(0, AES_KEY_V1, HMAC_KEY_V1)
        )));
    }

    @Test
    void 密钥为空应拒绝() {
        assertThrows(IllegalArgumentException.class, () -> new SecureExternalIdGenerator(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SecureExternalIdGenerator(null));
    }

    @Test
    void 输出长度恒为固定值() {
        SecureExternalIdGenerator g = newGenerator(0);
        for (long id : new long[]{0L, 1L, 255L, 65535L, (1L << 56) - 1}) {
            assertTrue(g.encode(id, 0).length() == FIXED_STR_LEN);
        }
    }
}
