package cc.nanoic.yunanexus.common.web.util;

import cc.nanoic.yunanexus.common.web.dto.UuidResult;
import cn.hutool.core.util.HexUtil;
import org.redisson.api.RedissonClient;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class UuidGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int[] CRC8_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x80) != 0) {
                    crc = ((crc << 1) ^ 0x31) & 0xFF;
                } else {
                    crc = (crc << 1) & 0xFF;
                }
            }
            CRC8_TABLE[i] = crc;
        }
    }

    public static UuidResult generate(RedissonClient redisson, byte[] aesKey) {
        int version = 1;
        long timestamp = System.currentTimeMillis() & 0x3FFFFFFFFFFL;
        int shardId = SECURE_RANDOM.nextInt(4096);
        long localId = redisson.getAtomicLong("uuid:seq").incrementAndGet() & 0xFFFFFFFFFFFFL;
        int randomVal = SECURE_RANDOM.nextInt(256);

        long high = ((long) version & 0xF) << 60
                | (timestamp << 18)
                | ((long) shardId & 0xFFF) << 6
                | ((localId >>> 42) & 0x3F);

        long low = ((localId & 0x3FFFFFFFFFFL) << 22)
                | ((long) randomVal & 0xFF) << 6;

        byte[] plaintext = new byte[16];
        ByteBuffer.wrap(plaintext).putLong(high).putLong(low);
        int crc = crc8(plaintext, 0, 15);
        low |= ((long) crc & 0xFF) << 14;
        ByteBuffer.wrap(plaintext, 8, 8).putLong(low);

        byte[] globalId = toBytes6(localId);
        String internalUuid = HexUtil.encodeHexStr(plaintext);
        String externalUuid = HexUtil.encodeHexStr(aesEncrypt(plaintext, aesKey));

        UuidResult result = new UuidResult();
        result.setGlobalId(globalId);
        result.setInternalUuid(internalUuid);
        result.setExternalUuid(externalUuid);
        return result;
    }

    public static byte[] extractGlobalId(String externalUuid, byte[] aesKey) {
        byte[] plaintext = aesDecrypt(HexUtil.decodeHex(externalUuid), aesKey);
        ByteBuffer buf = ByteBuffer.wrap(plaintext);
        long high = buf.getLong();
        long low = buf.getLong();
        long localId = ((high & 0x3F) << 42) | ((low >>> 22) & 0x3FFFFFFFFFFL);
        return toBytes6(localId);
    }

    public static int extractShardId(String externalUuid, byte[] aesKey) {
        byte[] plaintext = aesDecrypt(HexUtil.decodeHex(externalUuid), aesKey);
        long high = ByteBuffer.wrap(plaintext, 0, 8).getLong();
        return (int) ((high >>> 6) & 0xFFF);
    }

    public static String internalToExternal(String internalUuid, byte[] aesKey) {
        if (internalUuid == null || internalUuid.isEmpty()) {
            throw new IllegalArgumentException("internalUuid is null or empty");
        }
        if (internalUuid.length() != 32) {
            throw new IllegalArgumentException("internalUuid length must be 32, got " + internalUuid.length() + ": " + internalUuid.substring(0, Math.min(32, internalUuid.length())));
        }
        byte[] plaintext = HexUtil.decodeHex(internalUuid);
        byte[] ciphertext = aesEncrypt(plaintext, aesKey);
        return HexUtil.encodeHexStr(ciphertext);
    }

    private static byte[] toBytes6(long localId) {
        byte[] gid = new byte[6];
        gid[0] = (byte) (localId >>> 40);
        gid[1] = (byte) (localId >>> 32);
        gid[2] = (byte) (localId >>> 24);
        gid[3] = (byte) (localId >>> 16);
        gid[4] = (byte) (localId >>> 8);
        gid[5] = (byte) localId;
        return gid;
    }

    private static int crc8(byte[] data, int offset, int len) {
        int crc = 0x00;
        for (int i = 0; i < len; i++) {
            crc = CRC8_TABLE[(crc ^ data[offset + i]) & 0xFF];
        }
        return crc;
    }

    private static byte[] aesEncrypt(byte[] plaintext, byte[] aesKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败: " + e.getMessage()
                    + " (keyLen=" + (aesKey != null ? aesKey.length : 0)
                    + ", dataLen=" + (plaintext != null ? plaintext.length : 0) + ")", e);
        }
    }

    private static byte[] aesDecrypt(byte[] ciphertext, byte[] aesKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }
}
