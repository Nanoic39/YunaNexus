package cc.nanoic.yunanexus.common.core.util;

import cn.hutool.core.codec.Base62;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部 ID 编解码器（生产级）。<p>
 *
 * 用于将「内部 ID + 分片编号」编码为对外暴露的固定长度字符串，并可逆向解码。
 * 采用 AES-CTR + HMAC-SHA256 + Base62，具备以下生产级特性：<br>
 *
 * 1. <b>随机 IV</b>：每次编码生成 8 字节随机 IV，随 payload 一起编码，消除固定 IV 的
 *    many-time-pad 泄露（相同明文不会产生相同密文，攻击者无法通过 XOR 密文反推 ID 差值）。<br>
 * 2. <b>密钥版本化</b>：payload 首字节为密钥版本号，编码用最新版本密钥，解码按版本号
 *    选择对应历史密钥，实现密钥平滑轮换且历史外部 ID 不失效。<br>
 * 3. <b>完整性校验</b>：HMAC 覆盖「版本号 + IV + 密文」，采用恒定时间比较防时序攻击。<br>
 * 4. <b>解码自包含</b>：IV 编码在 payload 内，解码无需查表，任何持有密钥的节点均可解码。<p>
 *
 * <b>数据包结构（21 字节）：</b>
 * <pre>
 * [0]       版本号（1 字节，0~255）
 * [1..8]    IV（8 字节随机）
 * [9..16]   密文（8 字节，AES-CTR 加密明文）
 * [17..20]  HMAC 校验（4 字节，HMAC-SHA256(版本 || IV || 密文) 前 4 字节）
 * </pre>
 *
 * <b>明文（8 字节）：</b>
 * <pre>
 * bit 59-56: 分片编号（4 bit，0~15）
 * bit 55-0:  内部 ID（56 bit）
 * </pre>
 *
 * <b>固定输出长度：</b>21 字节（168 bit）Base62 编码最坏情况
 * {@code ceil(168 / log2(62)) = 29} 位，左补零至固定 29 位。<p>
 *
 * 说明：本类不依赖任何 Spring / Web 框架，保持 common-core 纯净。
 *
 * @author yunanexus
 */
public class SecureExternalIdGenerator {

    /** 版本号字节数 */
    private static final int VERSION_BYTES = 1;
    /** IV 字节数 */
    private static final int IV_BYTES = 8;
    /** 明文/密文字节数 */
    private static final int DATA_BYTES = 8;
    /** HMAC 校验字节数 */
    private static final int CHECKSUM_BYTES = 4;
    /** 数据包总字节数 */
    private static final int TOTAL_BYTES = VERSION_BYTES + IV_BYTES + DATA_BYTES + CHECKSUM_BYTES;
    /** 固定输出字符串长度 */
    private static final int FIXED_STR_LEN = 29;
    /** 内部 ID 掩码（56 bit） */
    private static final long INTERNAL_ID_MASK = 0x00FFFFFFFFFFFFFFL;
    /** 分片编号掩码（4 bit） */
    private static final int SHARD_INDEX_MASK = 0xF;
    /** AES 算法 */
    private static final String AES_ALGORITHM = "AES/CTR/NoPadding";
    /** HMAC 算法 */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    /** 用于生成随机 IV 的安全随机源 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 密钥版本表（version -> KeyVersion），不可变 */
    private final Map<Integer, KeyVersion> versionMap;

    /** 当前最新版本号，编码时使用 */
    private final int latestVersion;

    /**
     * 构造编解码器。
     *
     * @param keyVersions 密钥版本列表，version 不可重复；编码使用其中 version 最大者
     * @throws IllegalArgumentException keyVersions 为空、version 重复或密钥非法
     */
    public SecureExternalIdGenerator(List<KeyVersion> keyVersions) {
        if (keyVersions == null || keyVersions.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个密钥版本");
        }
        Map<Integer, KeyVersion> map = new HashMap<>();
        int latest = -1;
        for (KeyVersion kv : keyVersions) {
            if (map.containsKey(kv.getVersion())) {
                throw new IllegalArgumentException("密钥版本号重复: " + kv.getVersion());
            }
            map.put(kv.getVersion(), kv);
            latest = Math.max(latest, kv.getVersion());
        }
        this.versionMap = Collections.unmodifiableMap(map);
        this.latestVersion = latest;
    }

    /**
     * 将内部 ID 与分片编号编码为外部 ID。
     *
     * @param internalId 内部 ID（0 ~ 2^56-1）
     * @param shardIndex 分片编号（0 ~ 15）
     * @return 固定 29 位的外部 ID 字符串
     * @throws IllegalArgumentException 参数越界
     */
    public String encode(long internalId, int shardIndex) {
        validateInput(internalId, shardIndex);

        KeyVersion kv = versionMap.get(latestVersion);

        // 1. 生成 8 字节随机 IV
        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        // 2. 打包明文（8 字节）：分片编号(4 bit) + 内部 ID(56 bit)
        long dataLong = ((long) (shardIndex & SHARD_INDEX_MASK) << 56)
                | (internalId & INTERNAL_ID_MASK);
        byte[] plain = longToBytes(dataLong);

        // 3. AES-CTR 加密
        byte[] cipher = aesCtr(plain, iv, kv.getAesKey(), Cipher.ENCRYPT_MODE);

        // 4. 计算 HMAC（版本 || IV || 密文），取前 4 字节
        byte[] checksum = Arrays.copyOf(hmac(kv, buildHmacInput(latestVersion, iv, cipher)), CHECKSUM_BYTES);

        // 5. 组装 payload（21 字节）
        byte[] payload = new byte[TOTAL_BYTES];
        payload[0] = (byte) latestVersion;
        System.arraycopy(iv, 0, payload, VERSION_BYTES, IV_BYTES);
        System.arraycopy(cipher, 0, payload, VERSION_BYTES + IV_BYTES, DATA_BYTES);
        System.arraycopy(checksum, 0, payload, VERSION_BYTES + IV_BYTES + DATA_BYTES, CHECKSUM_BYTES);

        // 6. Base62 编码 + 左补零到固定长度
        String encoded = Base62.encode(payload);
        if (encoded.length() > FIXED_STR_LEN) {
            throw new IllegalStateException("编码后的外部 ID 超过固定长度: " + encoded.length());
        }
        return leftPad(encoded, FIXED_STR_LEN, '0');
    }

    /**
     * 将外部 ID 解码为内部 ID 与分片编号。
     *
     * @param externalId 外部 ID 字符串（固定 29 位）
     * @return 解码结果，含 internalId 与 shardIndex
     * @throws IllegalArgumentException 长度非法或 payload 非法
     * @throws SecurityException        HMAC 校验失败（被篡改）或密钥版本未知
     */
    public DecodeResult decode(String externalId) {
        if (externalId == null || externalId.length() != FIXED_STR_LEN) {
            throw new IllegalArgumentException(
                    "Invalid external ID length, expected " + FIXED_STR_LEN);
        }

        // 1. Base62 解码（处理 BigInteger 去前导零 / 符号字节）
        byte[] payload = decodeFixed(externalId, TOTAL_BYTES);

        // 2. 分离各字段
        int version = payload[0] & 0xFF;
        byte[] iv = Arrays.copyOfRange(payload, VERSION_BYTES, VERSION_BYTES + IV_BYTES);
        byte[] cipher = Arrays.copyOfRange(payload, VERSION_BYTES + IV_BYTES,
                VERSION_BYTES + IV_BYTES + DATA_BYTES);
        byte[] received = Arrays.copyOfRange(payload, TOTAL_BYTES - CHECKSUM_BYTES, TOTAL_BYTES);

        // 3. 按版本号选择密钥
        KeyVersion kv = versionMap.get(version);
        if (kv == null) {
            throw new SecurityException("Unknown key version: " + version);
        }

        // 4. 校验 HMAC（恒定时间比较）
        byte[] expected = Arrays.copyOf(hmac(kv, buildHmacInput(version, iv, cipher)), CHECKSUM_BYTES);
        if (!constantTimeEquals(received, expected)) {
            throw new SecurityException("External ID checksum mismatch");
        }

        // 5. 解密
        byte[] plain = aesCtr(cipher, iv, kv.getAesKey(), Cipher.DECRYPT_MODE);

        // 6. 解包
        long dataLong = bytesToLong(plain);
        int shardIndex = (int) ((dataLong >> 56) & SHARD_INDEX_MASK);
        long internalId = dataLong & INTERNAL_ID_MASK;

        return new DecodeResult(internalId, shardIndex);
    }

    // ---- 内部方法 ----

    /** 参数合法性校验 */
    private static void validateInput(long internalId, int shardIndex) {
        if (shardIndex < 0 || shardIndex > SHARD_INDEX_MASK) {
            throw new IllegalArgumentException("分片索引超出范围(0~15): " + shardIndex);
        }
        if (internalId < 0 || (internalId & ~INTERNAL_ID_MASK) != 0L) {
            throw new IllegalArgumentException("内部ID超出范围(0~2^56-1): " + internalId);
        }
    }

    /** 构造 HMAC 输入：版本字节 + IV + 密文 */
    private static byte[] buildHmacInput(int version, byte[] iv, byte[] cipher) {
        byte[] input = new byte[VERSION_BYTES + IV_BYTES + DATA_BYTES];
        input[0] = (byte) version;
        System.arraycopy(iv, 0, input, VERSION_BYTES, IV_BYTES);
        System.arraycopy(cipher, 0, input, VERSION_BYTES + IV_BYTES, DATA_BYTES);
        return input;
    }

    /** AES-CTR 加解密；8 字节 IV 扩展为 16 字节（低 8 字节全 0，仅加密单 block 不涉及 counter 递增） */
    private static byte[] aesCtr(byte[] input, byte[] iv8, byte[] aesKey, int mode) {
        try {
            byte[] ivFull = new byte[16];
            System.arraycopy(iv8, 0, ivFull, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(mode, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(ivFull));
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("AES 操作失败", e);
        }
    }

    /** 计算 HMAC-SHA256 */
    private static byte[] hmac(KeyVersion kv, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(kv.getHmacKey(), HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /** Base62 解码并归一化到固定长度（处理 BigInteger 去前导零 / 符号字节） */
    private static byte[] decodeFixed(String s, int expectedLen) {
        byte[] raw = Base62.decode(s);
        // 去掉 BigInteger.toByteArray 可能添加的符号前导 0
        int start = 0;
        while (start < raw.length - 1 && raw[start] == 0) {
            start++;
        }
        byte[] trimmed = Arrays.copyOfRange(raw, start, raw.length);
        if (trimmed.length > expectedLen) {
            throw new IllegalArgumentException(
                    "Invalid payload size: " + trimmed.length + ", expected " + expectedLen);
        }
        byte[] fixed = new byte[expectedLen];
        System.arraycopy(trimmed, 0, fixed, expectedLen - trimmed.length, trimmed.length);
        return fixed;
    }

    /** Long 转 8 字节（大端） */
    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    /** 8 字节（大端）转 Long */
    private static long bytesToLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    /** 左侧补齐字符串到指定长度 */
    private static String leftPad(String s, int len, char pad) {
        if (s.length() >= len) {
            return s;
        }
        return String.valueOf(pad).repeat(len - s.length()) + s;
    }

    /** 恒定时间比较，防止时序攻击 */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /** 解码结果 */
    public record DecodeResult(long internalId, int shardIndex) {
    }

    /**
     * 密钥版本。
     * <p>
     * 每个版本持有独立的 AES 密钥与 HMAC 密钥。version 用于在 payload 首字节标识，
     * 解码时据此选择对应密钥，实现密钥轮换。
     */
    public static final class KeyVersion {

        private final int version;
        private final byte[] aesKey;
        private final byte[] hmacKey;

        /**
         * 构造密钥版本。
         *
         * @param version      版本号（0~255）
         * @param aesKeyBase64 AES 密钥（Base64，16/24/32 字节）
         * @param hmacKeyBase64 HMAC 密钥（Base64）
         */
        public KeyVersion(int version, String aesKeyBase64, String hmacKeyBase64) {
            if (version < 0 || version > 255) {
                throw new IllegalArgumentException("密钥版本号超出范围(0~255): " + version);
            }
            if (aesKeyBase64 == null || aesKeyBase64.isBlank()) {
                throw new IllegalArgumentException("AES密钥不允许为空");
            }
            if (hmacKeyBase64 == null || hmacKeyBase64.isBlank()) {
                throw new IllegalArgumentException("HMAC密钥不允许为空");
            }
            byte[] aes = Base64.getDecoder().decode(aesKeyBase64);
            byte[] hmac = Base64.getDecoder().decode(hmacKeyBase64);
            if (aes.length != 16 && aes.length != 24 && aes.length != 32) {
                throw new IllegalArgumentException(
                        "AES密钥长度必须是16/24/32字节, 当前: " + aes.length);
            }
            this.version = version;
            this.aesKey = aes;
            this.hmacKey = hmac;
        }

        public int getVersion() {
            return version;
        }

        /** 返回 AES 密钥副本，防止外部修改 */
        public byte[] getAesKey() {
            return Arrays.copyOf(aesKey, aesKey.length);
        }

        /** 返回 HMAC 密钥副本，防止外部修改 */
        public byte[] getHmacKey() {
            return Arrays.copyOf(hmacKey, hmacKey.length);
        }
    }
}
